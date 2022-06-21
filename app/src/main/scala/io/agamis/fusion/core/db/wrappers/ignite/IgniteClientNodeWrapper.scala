package io.agamis.fusion.core.db.wrappers.ignite

import io.agamis.fusion.env.EnvContainer
import io.agamis.fusion.core.db.wrappers.ignite.exceptions.MissingIgniteConfException

import com.typesafe.config.ConfigException

import akka.actor.typed.{ActorSystem, Extension}

import org.apache.ignite.{Ignite, Ignition, IgniteCache, IgniteException}
import org.apache.ignite.configuration.{IgniteConfiguration, DeploymentMode, CacheConfiguration}
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi
import org.apache.ignite.spi.discovery.tcp.ipfinder.multicast.TcpDiscoveryMulticastIpFinder

import akka.actor.typed.ExtensionId

import org.slf4j.Logger

class IgniteClientNodeWrapper(system: ActorSystem[_]) extends Extension {

  private val cfg: IgniteConfiguration = new IgniteConfiguration()
  cfg.setClientMode(true)
  // doit aussi être activé dans les noeuds serveurs
  cfg.setPeerClassLoadingEnabled(true)
  cfg.setDeploymentMode(DeploymentMode.CONTINUOUS)

  private val ipFinder: TcpDiscoveryMulticastIpFinder = new TcpDiscoveryMulticastIpFinder()

  try {
    val nodeAddresses: java.util.List[String] = EnvContainer.getArray("fusion.core.db.ignite.nodes")
    ipFinder.setAddresses(nodeAddresses)
  } catch {
    case e: ConfigException =>
      throw MissingIgniteConfException("fusion.core.db.ignite.nodes Config is missing", e)
    case _: Throwable => throw new UnknownError("An unkown error occurred while setting ignite cluster's nodes addresses")
  }

  cfg.setDiscoverySpi(new TcpDiscoverySpi().setIpFinder(ipFinder))

  private var _ignite: Ignite = _

  try {
    _ignite = Ignition.start(cfg)
  } catch {
    case e: IgniteException =>
      system.log.error("An error occurred with ignite node" + e.getCause())
      println(e)
      system.terminate()
    case _: Throwable => throw new UnknownError("An unkown error occurred while starting ignite client node")
  }

  def getLogger: Logger = system.log

  def ignite: Ignite = _ignite

  def close(): Unit = ignite.close()

  // Let make a cache config manually
  def makeCacheConfig[K, V]: CacheConfiguration[K, V] = {
    new CacheConfiguration[K, V]
  }

  // Create a new cache from provided configuration
  def createCache[K, V](configuration: CacheConfiguration[K, V]): IgniteCache[K, V] = {
    ignite.createCache(configuration)
  }

  // Get a cache from ignite cluster to play with
  def getCache[K, V](cache: String, transactional: Boolean = false): IgniteCache[K, V] = {
    if (transactional) return ignite.cache(cache).withAllowAtomicOpsInTx()
    ignite.cache(cache)
  }

  def cacheExists(cache: String): Boolean = {
    ignite.cacheNames().contains(cache)
  }
}

object IgniteClientNodeWrapper extends ExtensionId[IgniteClientNodeWrapper] {
  // will only be called once
  def createExtension(system: ActorSystem[_]): IgniteClientNodeWrapper = new IgniteClientNodeWrapper(system)

  // Java API
  def get(system: ActorSystem[_]): IgniteClientNodeWrapper = apply(system)
}
