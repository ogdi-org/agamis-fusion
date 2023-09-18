package io.agamis.fusion.core.db.models.sql

import io.agamis.fusion.core.db.datastores.sql.ProfileStore
import io.agamis.fusion.core.db.models.sql.generics.Email
import io.agamis.fusion.core.db.models.sql.generics.exceptions.RelationNotFoundException
import io.agamis.fusion.core.db.models.sql.typed.Model
import org.apache.ignite.cache.query.annotations.QuerySqlField
import org.apache.ignite.transactions.Transaction

import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import scala.annotation.nowarn
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

/** A class that reflects the state of the profile entity in the database
  *
  * @param store
  *   an implicit parameter for accessing store methods
  */
class Profile(implicit @transient protected val store: ProfileStore)
    extends Model {

    @QuerySqlField(name = "alias", notNull = false)
    private var _alias: String = null

    /** Profile alias property
      *
      * @return
      *   profile alias [[java.lang.String String]]
      */
    def alias: Option[String] = Some(_alias)

    /** A method for setting alias property
      *
      * @param alias
      *   a [[java.lang.String String]] to assign to profile alias property
      * @return
      *   this object
      */
    def setAlias(alias: String): Profile = {
        _alias = alias
        this
    }

    @QuerySqlField(name = "lastname", notNull = true)
    private var _lastname: String = null

    /** Profile lastname property
      *
      * @return
      *   profile lastname [[java.lang.String String]]
      */
    def lastname: String = _lastname

    /** A method for setting lastname property
      *
      * @param lastname
      *   a [[java.lang.String String]] to assign to profile lastname property
      * @return
      *   this object
      */
    def setLastname(lastname: String): Profile = {
        _lastname = lastname
        this
    }

    @QuerySqlField(name = "firstname", notNull = true)
    private var _firstname: String = null

    /** Profile firstname property
      *
      * @return
      *   profile firstname [[java.lang.String String]]
      */
    def firstname: String = _firstname

    /** A method for setting firstname property
      *
      * @param firstname
      *   a [[java.lang.String String]] to assign to profile firstname property
      * @return
      *   this object
      */
    def setFirstname(firstname: String): Profile = {
        _firstname = firstname
        this
    }

    @QuerySqlField(name = "last_login", notNull = false)
    private var _lastLogin: Timestamp = null

    /** Profile property that reflects datetime of the last time user logged in
      * with this profile
      *
      * @return
      *   creation date [[java.sql.Timestamp Timestamp]]
      */
    def lastLogin: Timestamp = _lastLogin

    /** A method for setting lastLogin property
      *
      * @param lastLogin
      *   a [[java.sql.Timestamp Timestamp]] to assign to entity lastLogin
      *   property
      * @return
      */
    def setLastLogin(lastLogin: Timestamp): Profile = {
        _lastLogin = lastLogin
        this
    }

    @QuerySqlField(name = "is_active", notNull = true)
    private var _isActive: Boolean = false

    /** Profile property that reflects current whether active or inactive state
      *
      * @return
      *   isActive [[scala.Boolean Boolean]] state
      */
    def isActive: Boolean = _isActive

    /** A method for setting isActive property to true
      *
      * @return
      *   this object
      */
    def setActive(): Profile = {
        _isActive = true
        this
    }

    /** A method for setting isActive property to false
      *
      * @return
      *   this object
      */
    def setInactive(): Profile = {
        _isActive = false
        this
    }

    @QuerySqlField(name = "user_id", notNull = true)
    private var _userId: UUID = _

    /** Profile property that reflects related user's id
      *
      * @return
      *   userId [[java.util.UUID]]
      */
    def userId: UUID = _userId

    @transient
    private var _relatedUser: Option[User] = None

    /** Profile property that reflects the user to which this profile is related
      *
      * @return
      *   related [[io.agamis.fusion.core.db.models.sql.User User]]
      */
    def relatedUser: Option[User] = _relatedUser

    /** A method for setting profile/user relation
      *
      * This method automatically set userId foreign key
      *
      * @param user
      *   a [[io.agamis.fusion.core.db.models.sql.User User]] to assign to this
      *   relation
      * @return
      *   this object
      */
    def setRelatedUser(user: User): Profile = {
        _relatedUser = Some(user)
        _userId = user.id
        this
    }

    @transient
    private var _mainEmail: Email = _
    def mainEmail: Email          = _mainEmail
    def setMainEmail(email: Email): Profile = {
        _emails.indexWhere(_._2.id == email.id) match {
            case -1 => {}
            case index =>
                _emails =
                    _emails.updated(index, _emails(index).copy(_1 = false))
        }
        _mainEmail = email
        this
    }

    @transient
    private var _emails: List[(Boolean, Email)] = List()
    def emails: List[(Boolean, Email)]          = _emails
    def addEmail(email: Email): Profile = {
        _emails.indexWhere(_._2.id == email.id) match {
            case -1 => _emails ::= (true, email)
            case index =>
                _emails = _emails.updated(index, _emails(index).copy(_1 = true))
        }
        this
    }
    def removeEmail(email: Email): Profile = {
        _emails.indexWhere(_._2.id == email.id) match {
            case -1 => throw new RelationNotFoundException()
            case index =>
                _emails =
                    _emails.updated(index, _emails(index).copy(_1 = false))
        }
        this
    }

    @transient
    private var _groups: List[(Boolean, Group)] = List()
    def groups: List[(Boolean, Group)]          = _groups
    def addGroup(group: Group): Profile = {
        _groups.indexWhere(_._2.id == group.id) match {
            case -1 => _groups ::= (true, group)
            case index =>
                _groups = _groups.updated(index, _groups(index).copy(_1 = true))
        }
        this
    }
    def removeGroup(group: Group): Profile = {
        _groups.indexWhere(_._2.id == group.id) match {
            case -1 => throw new RelationNotFoundException()
            case index =>
                _groups =
                    _groups.updated(index, _groups(index).copy(_1 = false))
        }
        this
    }

    @transient
    private var _permissions: List[(Boolean, Permission)] = List()
    def permissions: List[(Boolean, Permission)]          = _permissions
    def addPermission(permission: Permission): Profile = {
        _permissions.indexWhere(_._2.id == permission.id) match {
            case -1 => _permissions ::= (true, permission)
            case index =>
                _permissions = _permissions.updated(
                  index,
                  _permissions(index).copy(_1 = true)
                )
        }
        this
    }
    def removePermission(permission: Permission): Profile = {
        _permissions.indexWhere(_._2.id == permission.id) match {
            case -1 => throw new RelationNotFoundException()
            case index =>
                _permissions = _permissions.updated(
                  index,
                  _permissions(index).copy(_1 = false)
                )
        }
        this
    }

    @QuerySqlField(name = "organization_id", notNull = true)
    @nowarn
    private var _organizationId: UUID = _

    @transient
    private var _relatedOrganization: Option[Organization] = None

    /** Profile property that reflects the organization to which this profile is
      * related
      *
      * @return
      *   related
      *   [[io.agamis.fusion.core.db.models.sql.Organization Organization]]
      */
    def relatedOrganization: Option[Organization] = _relatedOrganization

    /** A method for setting profile/organization relation
      *
      * This method automatically set organizationId foreign key
      *
      * @param organization
      *   a [[io.agamis.fusion.core.db.models.sql.Organization Organization]] to
      *   assign to this relation
      * @return
      *   this object
      */
    def setRelatedOrganization(organization: Organization): Profile = {
        _relatedOrganization = Some(organization)
        _organizationId = organization.id
        this
    }

    /** @inheritdoc */
    def persist(implicit
        ec: ExecutionContext
    ): Future[(Transaction, Profile)] = {
        this.setUpdatedAt(Timestamp.from(Instant.now()))
        store
            .persistProfile(this)
            .transformWith({
                case Success(tx) => Future.successful((tx, this))
                case Failure(e)  => Future.failed(e)
            })
    }

    /** @inheritdoc */
    def remove(implicit
        ec: ExecutionContext
    ): Future[(Transaction, Profile)] = {
        store
            .deleteProfile(this)
            .transformWith({
                case Success(tx) => Future.successful((tx, this))
                case Failure(e)  => Future.failed(e)
            })
    }
}
