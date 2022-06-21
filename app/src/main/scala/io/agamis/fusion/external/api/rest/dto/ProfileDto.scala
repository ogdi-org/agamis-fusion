package io.agamis.fusion.external.api.rest.dto.profile

import java.time.Instant
import java.util.UUID
import io.agamis.fusion.external.api.rest.dto.permission.PermissionDto
import io.agamis.fusion.external.api.rest.dto.organization.OrganizationDto
import io.agamis.fusion.core.db.models.sql.Profile

import io.agamis.fusion.external.api.rest.dto.common.JsonFormatters._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

/**
  * Profile DTO with JSON support
  *
  * @param id
  * @param lastName
  * @param firstName
  * @param mainEmail
  * @param emails
  * @param permissions
  * @param organization
  * @param lastLogin
  * @param userId
  * @param createdAt
  * @param updatedAt
  */
final case class ProfileDto(
    id: Option[UUID],
    lastName: String,
    firstName: String,
    mainEmail: String,
    emails: List[String],
    permissions: Option[List[PermissionDto]],
    organization: Option[OrganizationDto],
    lastLogin: Instant,
    userId: Option[String],
    createdAt: Option[Instant],
    updatedAt: Option[Instant]
)

object ProfileDto {
  def from(p: Profile): ProfileDto = {
    apply(
      Some(p.id),
      p.lastname,
      p.firstname,
      p.mainEmail.address,
      p.emails.filter(_._1 == true).map(_._2.address),
      Some(p.permissions.filter(_._1 == true).map(r => PermissionDto.from(r._2))),
      Some(OrganizationDto.from(p.relatedOrganization.orNull)),
      p.lastLogin.toInstant,
      Some(p.relatedUser.getOrElse(null).id.toString),
      Some(p.createdAt.toInstant),
      Some(p.updatedAt.toInstant)
    )
  }

  def apply(
    id: Option[UUID],
    lastName: String,
    firstName: String,
    mainEmail: String,
    emails: List[String],
    permissions: Option[List[PermissionDto]],
    organization: Option[OrganizationDto],
    lastLogin: Instant,
    userId: Option[String],
    createdAt: Option[Instant],
    updatedAt: Option[Instant]
  ): ProfileDto = {
    ProfileDto(
      id,
      lastName,
      firstName,
      mainEmail,
      emails,
      permissions,
      organization,
      lastLogin,
      userId,
      createdAt,
      updatedAt
    )
  }
}

trait ProfileJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
    import io.agamis.fusion.external.api.rest.dto.permission.PermissionJsonProtocol._
    import io.agamis.fusion.external.api.rest.dto.organization.OrganizationJsonProtocol._

    implicit val profileFormat: RootJsonFormat[ProfileDto] = jsonFormat11(ProfileDto.apply)
}

object ProfileJsonProtocol extends ProfileJsonSupport
