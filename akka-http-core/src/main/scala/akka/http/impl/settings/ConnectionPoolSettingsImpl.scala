/*
 * Copyright (C) 2017-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.http.impl.settings

import akka.annotation.InternalApi
import akka.http.impl.util._
import akka.http.scaladsl.settings.{ ClientConnectionSettings, ConnectionPoolSettings, PoolImplementation }
import com.typesafe.config.Config

import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration

/** INTERNAL API */
@InternalApi
private[akka] final case class ConnectionPoolSettingsImpl(
  maxConnections:                    Int,
  minConnections:                    Int,
  maxRetries:                        Int,
  maxOpenRequests:                   Int,
  pipeliningLimit:                   Int,
  maxConnectionLifetime:             Duration,
  baseConnectionBackoff:             FiniteDuration,
  maxConnectionBackoff:              FiniteDuration,
  idleTimeout:                       Duration,
  connectionSettings:                ClientConnectionSettings,
  poolImplementation:                PoolImplementation,
  responseEntitySubscriptionTimeout: Duration)
  extends ConnectionPoolSettings {

  require(maxConnections > 0, "max-connections must be > 0")
  require(minConnections >= 0, "min-connections must be >= 0")
  require(minConnections <= maxConnections, "min-connections must be <= max-connections")
  require(maxRetries >= 0, "max-retries must be >= 0")
  require(maxOpenRequests > 0, "max-open-requests must be a power of 2 > 0.")
  require((maxOpenRequests & (maxOpenRequests - 1)) == 0, "max-open-requests must be a power of 2. " + suggestPowerOfTwo(maxOpenRequests))
  require(pipeliningLimit > 0, "pipelining-limit must be > 0")
  require(maxConnectionLifetime > Duration.Zero, "max-connection-lifetime must be > 0")
  require(
    maxConnectionLifetime == Duration.Inf || poolImplementation == PoolImplementation.New,
    "max-connection-lifetime does not taking effect with legacy pool implementation")
  require(idleTimeout >= Duration.Zero, "idle-timeout must be >= 0")
  require(
    minConnections == 0 || (baseConnectionBackoff.toMillis > 0 && maxConnectionBackoff.toMillis > 10),
    "If min-connections > 0, you need to set a base-connection-backoff must be > 0 and max-connection-backoff must be > 10 millis " +
      "to avoid client pools excessively trying to open up new connections.")

  override def productPrefix = "ConnectionPoolSettings"

  def withUpdatedConnectionSettings(f: ClientConnectionSettings => ClientConnectionSettings): ConnectionPoolSettingsImpl =
    copy(connectionSettings = f(connectionSettings))

  private def suggestPowerOfTwo(around: Int): String = {
    val firstBit = 31 - Integer.numberOfLeadingZeros(around)

    val below = 1 << firstBit
    val above = 1 << (firstBit + 1)

    s"Perhaps try $below or $above."
  }
}

/** INTERNAL API */
@InternalApi
private[akka] object ConnectionPoolSettingsImpl extends SettingsCompanionImpl[ConnectionPoolSettingsImpl]("akka.http.host-connection-pool") {
  def fromSubConfig(root: Config, c: Config): ConnectionPoolSettingsImpl = {
    new ConnectionPoolSettingsImpl(
      c.getInt("max-connections"),
      c.getInt("min-connections"),
      c.getInt("max-retries"),
      c.getInt("max-open-requests"),
      c.getInt("pipelining-limit"),
      c.getPotentiallyInfiniteDuration("max-connection-lifetime"),
      c.getFiniteDuration("base-connection-backoff"),
      c.getFiniteDuration("max-connection-backoff"),
      c.getPotentiallyInfiniteDuration("idle-timeout"),
      ClientConnectionSettingsImpl.fromSubConfig(root, c.getConfig("client")),
      c.getString("pool-implementation").toLowerCase match {
        case "legacy" => PoolImplementation.Legacy
        case "new"    => PoolImplementation.New
      },
      c.getPotentiallyInfiniteDuration("response-entity-subscription-timeout")
    )
  }
}
