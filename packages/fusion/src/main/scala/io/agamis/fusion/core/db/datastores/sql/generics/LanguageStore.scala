package io.agamis.fusion.core.db.datastores.sql.generics

import io.agamis.fusion.core.db.common.Utils
import io.agamis.fusion.core.db.datastores.sql.exceptions.NoEntryException
import io.agamis.fusion.core.db.datastores.sql.generics.exceptions.languages.DuplicateLanguageException
import io.agamis.fusion.core.db.datastores.sql.generics.exceptions.languages.LanguageNotFoundException
import io.agamis.fusion.core.db.datastores.sql.generics.exceptions.languages.LanguageNotPersistedException
import io.agamis.fusion.core.db.datastores.sql.generics.exceptions.languages.LanguageQueryExecutionException
import io.agamis.fusion.core.db.datastores.typed.SqlMutableStore
import io.agamis.fusion.core.db.datastores.typed.sql.EntityQueryParams
import io.agamis.fusion.core.db.datastores.typed.sql.SqlStoreQuery
import io.agamis.fusion.core.db.models.sql.generics.Language
import io.agamis.fusion.core.db.wrappers.ignite.IgniteClientNodeWrapper
import org.apache.ignite.IgniteCache
import org.apache.ignite.cache.CacheAtomicityMode
import org.apache.ignite.cache.CacheMode

import java.util.UUID
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

class LanguageStore(implicit wrapper: IgniteClientNodeWrapper)
    extends SqlMutableStore[UUID, Language] {

    override val schema: String = "FUSION"
    override val cache: String  = s"SQL_${schema}_LANGUAGE"
    override protected val igniteCache: IgniteCache[UUID, Language] =
        if (wrapper.cacheExists(cache)) {
            wrapper.getCache[UUID, Language](cache)
        } else {
            wrapper.createCache[UUID, Language](
              wrapper
                  .makeCacheConfig[UUID, Language]
                  .setCacheMode(CacheMode.REPLICATED)
                  .setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL)
                  .setDataRegionName("Fusion")
                  .setName(cache)
                  .setSqlSchema(schema)
                  .setIndexedTypes(classOf[UUID], classOf[Language])
            )
        }

    def makeLanguageQuery(
        queryFilters: LanguageStore.GetLanguagesFilters
    ): SqlStoreQuery = {
        var queryString: String =
            "SELECT id, code, label " +
                "FROM FUSION.LANGUAGE AS LANGUAGE"
        val queryArgs: ListBuffer[String]       = ListBuffer()
        val whereStatements: ListBuffer[String] = ListBuffer()
        queryFilters.filters.foreach({ filter =>
            val innerWhereStatement: ListBuffer[String] = ListBuffer()
            // manage ids search
            if (filter.id.nonEmpty) {
                innerWhereStatement += s"language_id in (${(for (_ <- 1 to filter.id.length)
                        yield "?").mkString(",")})"
                queryArgs ++= filter.id
            }
            // manage codes search
            if (filter.code.nonEmpty) {
                innerWhereStatement += s"language_code in (${(for (_ <- 1 to filter.code.length)
                        yield "?").mkString(",")})"
                queryArgs ++= filter.code
            }
            // manage label search
            if (filter.label.nonEmpty) {
                innerWhereStatement += s"language_label in (${(for (_ <- 1 to filter.label.length)
                        yield "?").mkString(",")})"
                queryArgs ++= filter.label
            }
            whereStatements += innerWhereStatement.mkString(" AND ")
        })
        // compile whereStatements
        if (whereStatements.nonEmpty) {
            queryString += " WHERE " + whereStatements.reverse.mkString(" OR ")
        }
        // manage order
        if (queryFilters.orderBy.nonEmpty) {
            queryString += s" ORDER BY ${queryFilters.orderBy
                    .map(o =>
                        s"LANGUAGE.${o._1} ${o._2 match {
                                case 1  => "ASC"
                                case -1 => "DESC"
                            }}"
                    )
                    .mkString(", ")}"
        }
        makeQuery(queryString)
            .setParams(queryArgs.toList)
    }

    private def getLanguages(
        queryFilters: LanguageStore.GetLanguagesFilters
    )(implicit ec: ExecutionContext): Future[List[Language]] = {
        executeQuery(makeLanguageQuery(queryFilters)).transformWith({
            case Success(languageResults) =>
                val languages = languageResults.map(row => {
                    Language.apply
                        .setId(row.head.toString)
                        .setCode(row(1).toString)
                        .setLabel(row(2).toString)
                })
                Future.successful(languages.toList)
            case Failure(_) =>
                Future.failed(new LanguageQueryExecutionException)
        })
    }

    def getAllLanguages(implicit
        ec: ExecutionContext
    ): Future[List[Language]] = {
        getLanguages(LanguageStore.GetLanguagesFilters.none).transformWith({
            case Success(languages) =>
                languages.length match {
                    case 0 =>
                        Future.failed(
                          NoEntryException("Language store is empty")
                        )
                    case _ => Future.successful(languages)
                }
            case Failure(cause) => Future.failed(cause)
        })
    }

    private def getLanguageByCode(
        code: String
    )(implicit ec: ExecutionContext): Future[Language] = {
        getLanguages(
          LanguageStore.GetLanguagesFilters(
            List(
              LanguageStore.GetLanguagesFilter(
                List(),
                List(code),
                List()
              )
            ),
            List()
          )
        ).transformWith({
            case Success(languages) =>
                languages.length match {
                    case 0 =>
                        Future.failed(
                          LanguageNotFoundException(
                            s"Language $code couldn't be found"
                          )
                        )
                    case 1 => Future.successful(languages.head)
                    case _ => Future.failed(new DuplicateLanguageException)
                }
            case Failure(cause) => Future.failed(cause)
        })
    }

    def getOrCreateLanguage(code: String, label: String)(implicit
        ec: ExecutionContext
    ): Future[Language] = {
        getLanguageByCode(code).transformWith({
            case Success(language) => Future.successful(language)
            case Failure(cause) =>
                cause match {
                    case _: LanguageNotFoundException =>
                        val language = Language.apply
                            .setId(UUID.randomUUID().toString)
                            .setCode(code)
                            .setLabel(label)
                        Utils
                            .igniteToScalaFuture(
                              igniteCache.putAsync(
                                language.id,
                                language
                              )
                            )
                            .transformWith({
                                case Success(_) => Future.successful(language)
                                case Failure(cause) =>
                                    Future.failed(
                                      LanguageNotPersistedException(cause)
                                    )
                            })
                    case _ => Future.failed(new Error())
                }
        })
    }

    def updateLanguage(
        language: Language
    )(implicit ec: ExecutionContext): Future[Unit] = {
        Utils
            .igniteToScalaFuture(
              igniteCache.putAsync(
                language.id,
                language
              )
            )
            .transformWith({
                case Success(_) => Future.unit
                case Failure(cause) =>
                    Future.failed(LanguageNotPersistedException(cause))
            })
    }
}

object LanguageStore {
    case class GetLanguagesFilter(
        id: List[String],
        code: List[String],
        label: List[String]
    )
    case class GetLanguagesFilters(
        filters: List[GetLanguagesFilter] = List(),
        orderBy: List[(EntityQueryParams.Column, Int)] =
            List(), // (column, direction)
        pagination: Option[EntityQueryParams.Pagination] =
            None // (limit, offset)
    ) extends EntityQueryParams

    object GetLanguagesFilters {
        def none: GetLanguagesFilters = {
            GetLanguagesFilters(
              List(),
              List()
            )
        }
    }

    object Column {
        case class ID(val order: Int = 0, val name: String = "p.ID")
            extends EntityQueryParams.Column
    }
}
