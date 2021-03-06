package services

import java.time.ZonedDateTime
import javax.inject.{Inject, Singleton}

import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.name.Named
import com.markatta.timeforscala._
import model.FeedSource
import play.api.Logger
import play.api.inject.ApplicationLifecycle
import services.FeedFetcherActor.{FetchFeeds, FullReload}
import model.Utils._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FeedUpdater @Inject()(feedStore: FeedStore,
                            actorSystem: ActorSystem,
                            @Named("feed-fetcher-actor") feedFetcher: ActorRef,
                            lifecycle: ApplicationLifecycle)
                           (implicit exec: ExecutionContext) {
  val refreshCycle = 30.minutes
  val refreshCycleMinutes = refreshCycle.toMinutes.toInt
  val refreshInterval = 1.minute

  Logger.info("Scheduling updates...")

  for (x <- 1 to refreshCycleMinutes) {
    actorSystem.scheduler.schedule(refreshInterval * x, refreshCycle) {
      Logger.info("Running refresh cycle #" + x + "/" + refreshCycleMinutes)

      feedStore.loadSources recover { case t =>
        Logger.warn("Failed to load sources", t)
        Seq.empty
      } foreach { sources =>
        if (x == 1)
          refreshBacklog(sources)

        val now = ZonedDateTime.now

        val outdatedSources = sources.filter(_.timestamp.forall(now > _.plusMinutes(refreshCycleMinutes * 2)))

        val intervalSources = sources
          .filter(_.timestamp.forall(now > _.plusMinutes(refreshCycleMinutes)))
          .sortBy(_.timestamp)
          .take((sources.size + refreshCycleMinutes - 1) / refreshCycleMinutes)

        update((Set.empty ++ outdatedSources ++ intervalSources).toSeq)
      }
    } tap { reloadTask =>
      lifecycle.addStopHook { () =>
        Future(reloadTask.cancel)
      }
    }
  }

  def batch(cycleLength: Int)(sources: Seq[FeedSource]): Seq[Seq[FeedSource]] = {
    sources.grouped((sources.size + cycleLength - 1) / cycleLength).toSeq
  }

  def refreshBacklog(sources: Seq[FeedSource]): Unit = {
    Logger.info(s"###> Refreshing backlog of ${sources.size} sources...")

    feedFetcher ! FullReload(sources)
  }

  def update(sources: Seq[FeedSource]): Unit = {
    Logger.info(s"###> Updating ${sources.size} sources...")

    feedFetcher ! FetchFeeds(sources)
  }
}
