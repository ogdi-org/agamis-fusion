package io.agamis.fusion.external.api.rest.routes

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import io.agamis.fusion.core.actors.data.DataActor
import io.agamis.fusion.core.actors.data.entities.UserDataBehavior
import io.agamis.fusion.external.api.rest.dto.user.UserApiJsonSupport
import io.agamis.fusion.external.api.rest.dto.user.UserApiResponse
import io.agamis.fusion.external.api.rest.dto.user.UserDto
import io.agamis.fusion.external.api.rest.dto.user.UserQuery

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success
import io.agamis.fusion.external.api.rest.dto.user.UserMutation
import spray.json._
import akka.http.scaladsl.server.Directives
import io.agamis.fusion.external.api.rest.dto.profile.ProfileDto
import io.agamis.fusion.external.api.rest.dto.organization.OrganizationDto
import java.time.Instant
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey

/** Class User Routes
  *
  * @param system
  * @param data
  */
class UserRoutes()(implicit system: ActorSystem[_]) extends UserApiJsonSupport {

  import akka.actor.typed.scaladsl.AskPattern.schedulerFromActorSystem
  import akka.actor.typed.scaladsl.AskPattern.Askable

  import io.agamis.fusion.external.api.rest.dto.user.SingleUserResponse
  import io.agamis.fusion.external.api.rest.dto.user.UserQueryResponse
  import io.agamis.fusion.external.api.rest.dto.common.typed.ApiStatus

  import io.agamis.fusion.core.actors.data.entities.UserDataBehavior.MultiUserState
  import io.agamis.fusion.core.actors.data.entities.UserDataBehavior.SingleUserState

  private implicit val ec: ExecutionContext = system.executionContext
  private val sharding = ClusterSharding(system)
  private val TypeKey = EntityTypeKey[DataActor.Command](DataActor.DataShardName)

  // asking someone requires a timeout and a scheduler, if the timeout hits without response
  // the ask is failed with a TimeoutException
  implicit val timeout = Timeout(10.seconds)

  def mapToApiResponse(r: UserDataBehavior.Response): UserApiResponse = {
      (r match {
        case SingleUserState(_, result, status) =>
          status match {
            case ok: UserDataBehavior.Ok =>
              result match {
                case Some(u) => SingleUserResponse(Some(UserDto.from(u)), ApiStatus(StatusCodes.OK, ok.msg))
                case None => SingleUserResponse(None, ApiStatus(StatusCodes.InternalServerError, s"Datastore responded '${ok.msg}' but provided an empty result"))
              }
            case nfound: UserDataBehavior.NotFound =>
              SingleUserResponse(None, ApiStatus(StatusCodes.NotFound, nfound.msg))
            case exception: UserDataBehavior.InternalException => 
              SingleUserResponse(None, ApiStatus(StatusCodes.InternalServerError, exception.msg))
          }
        case MultiUserState(_, result, status) => 
          status match {
            case ok: UserDataBehavior.Ok =>
              UserQueryResponse(result.map(u => UserDto.from(u)), ApiStatus(StatusCodes.OK, ok.msg))
            case nfound: UserDataBehavior.NotFound =>
              UserQueryResponse(List(), ApiStatus(StatusCodes.NotFound, nfound.msg))
            case exception: UserDataBehavior.InternalException => 
              SingleUserResponse(None, ApiStatus(StatusCodes.InternalServerError, exception.msg))
          }
      })
  }

  def getUserById(id: UUID): Future[UserDataBehavior.Response] = {
    sharding.entityRefFor(TypeKey, "user-%s".format(id.toString())).ask { ref: ActorRef[UserDataBehavior.Response] =>
      UserDataBehavior.GetUserById(ref, id)
    }
  }

  def getUserByUsername(username: String): Future[UserDataBehavior.Response] = {
    sharding.entityRefFor(TypeKey, "user-uname-%s".format(username)).ask { ref: ActorRef[UserDataBehavior.Response] =>
      UserDataBehavior.GetUserByUsername(ref, username)
    }
  }

  def queryUsers(query: UserDataBehavior.Query): Future[UserDataBehavior.Response] = {
    sharding.entityRefFor(TypeKey, "user-query-%d".format(query.hashCode)).ask { ref: ActorRef[UserDataBehavior.Response] =>
      UserDataBehavior.ExecuteQuery(ref, query)
    }
  }

  def createUser(uMut: UserDataBehavior.UserMutation): Future[UserDataBehavior.Response] = {
    sharding.entityRefFor(TypeKey, "user-%s".format(UUID.randomUUID)).ask { ref: ActorRef[UserDataBehavior.Response] =>
      UserDataBehavior.CreateUser(ref, uMut)
    }
  }

  def updateUser(id: UUID, uMut: UserDataBehavior.UserMutation): Future[UserDataBehavior.Response] = {
    sharding.entityRefFor(TypeKey, "user-%s".format(id.toString)).ask { ref: ActorRef[UserDataBehavior.Response] =>
      UserDataBehavior.UpdateUser(ref, id, uMut)
    }
  }

  def deleteUser(id: UUID): Future[UserDataBehavior.Response] = {
    sharding.entityRefFor(TypeKey, "user-%s".format(id.toString)).ask { ref: ActorRef[UserDataBehavior.Response] =>
      UserDataBehavior.DeleteUser(ref, id)
    }
  }

  import io.agamis.fusion.core.actors.data.entities.UserDataBehavior.Field

  val routes =
    concat(
      path("users")(
        concat(
          // query on all users
          get {
            parameters(
              Field.ID.as[List[String]].optional,
              Field.USERNAME.as[List[String]].optional,
              Field.OFFSET.as[Int].optional,
              Field.LIMIT.as[Int].optional,
              Field.CREATED_AT.as[List[(String, String)]].optional,
              Field.UPDATED_AT.as[List[(String, String)]].optional,
              Field.ORDER_BY.as[List[(String, Int)]].optional
            ).as(UserQuery.apply _) { queryString =>
              val query: UserDataBehavior.Query = UserDataBehavior.Query(
                queryString.id.map(UUID.fromString(_)),
                queryString.username,
                queryString.offset,
                queryString.limit,
                queryString.createdAt,
                queryString.updatedAt,
                queryString.orderBy
              )
              onComplete(queryUsers(query)) {
                case Success(resp) =>
                  mapToApiResponse(resp) match {
                    case SingleUserResponse(result, status) => status.code match {
                      case StatusCodes.Success(_) => complete(status.code, result)
                      case _                      => complete(status.code, status.message)
                    }
                    case UserQueryResponse(result, status) => status.code match {
                      case StatusCodes.Success(_) => complete(status.code, result)
                      case _                      => complete(status.code, status.message)
                    }
                    case _ => complete(StatusCodes.InternalServerError, "Bad response format from internal actor")
                  }
                case Failure(cause) => complete(StatusCodes.InternalServerError, cause)
              }
            }
          },
          // create user
          post {
            entity(as[UserMutation]) { uMut =>
              onComplete(createUser(UserDataBehavior.UserMutation(Some(uMut.username), Some(uMut.password)))) {
                case Success(resp) =>
                  mapToApiResponse(resp) match {
                    case SingleUserResponse(result, status) => complete(status.code, result)
                    case _ => complete(StatusCodes.InternalServerError, "Bad response format from internal actor")
                  }
                case Failure(cause) => complete(StatusCodes.InternalServerError, cause)
              }
            }
          }
        )
      ),
      pathPrefix("user")(
        concat(
          //get by username
          get {
            parameters(Field.USERNAME.as[String]) { (username) =>
              onComplete(getUserByUsername(username)) {
                case Success(resp) =>
                  mapToApiResponse(resp) match {
                    case SingleUserResponse(result, status) => complete(status.code, result)
                    case _ => complete(StatusCodes.InternalServerError, "Bad response format from internal actor")
                  }
                case Failure(cause) => complete(StatusCodes.InternalServerError, cause)
              }
            }
          },
          // user id segment
          path(Segment) { id: String =>
            concat(
              //get by id
              get {
                onComplete(getUserById(UUID.fromString(id))) {
                  case Success(resp) => 
                    mapToApiResponse(resp) match {
                      case SingleUserResponse(result, status) => complete(status.code, result)
                      case _ => complete(StatusCodes.InternalServerError, "Bad response format from internal actor")
                    }
                  case Failure(cause) => complete(StatusCodes.InternalServerError, cause)
                }
              },
              // update user
              put {
                entity(as[UserMutation]) { umut =>
                  onComplete(updateUser(
                    UUID.fromString(id),
                    UserDataBehavior.UserMutation(Some(umut.username), Some(umut.password))
                  )) {
                    case Success(resp) =>
                      mapToApiResponse(resp) match {
                        case SingleUserResponse(result, status) => complete(status.code, result)
                        case _ => complete(StatusCodes.InternalServerError, "Bad response format from internal actor")
                      }
                    case Failure(cause) => complete(StatusCodes.InternalServerError, cause)
                  }
                }
              },
              // delete user
              delete {
                onComplete(deleteUser(UUID.fromString(id))) {
                  case Success(resp) => 
                    mapToApiResponse(resp) match {
                      case SingleUserResponse(result, status) => complete(status.code, result)
                      case _ => complete(StatusCodes.InternalServerError, "Bad response format from internal actor")
                    }
                  case Failure(cause) => complete(StatusCodes.InternalServerError, cause)
                }
              }
            )
          }
        )
      )
    )
}
