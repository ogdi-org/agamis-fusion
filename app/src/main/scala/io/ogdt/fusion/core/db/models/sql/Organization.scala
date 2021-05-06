package io.ogdt.fusion.core.db.models.sql

import io.ogdt.fusion.core.db.datastores.sql.OrganizationStore
import io.ogdt.fusion.core.db.models.sql.typed.Model

import org.apache.ignite.cache.query.annotations.QuerySqlField

import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import java.sql.Timestamp
import scala.util.Success
import scala.util.Failure
import akka.protobufv3.internal.compiler.PluginProtos.CodeGeneratorResponse.File

class Organization(implicit protected val store: OrganizationStore) extends Model with Serializable {

    @QuerySqlField(index = true, name = "id", notNull = true)
    protected var _id: UUID = null
    def id: UUID = _id
    // Used to set UUID (mainly for setting uuid of existing user when fetching)
    def setId(id: String): Organization = {
        _id = UUID.fromString(id)
        this
    }

    @QuerySqlField(name = "label", notNull = true)
    private var _label: String = null
    def label: String = _label
    def setLabel(label: String): Organization = {
        _label = label
        this
    }

    @QuerySqlField(name = "type", notNull = true)
    private var _type: String = null
    def `type`: String = _type
    def setType(`type`: String): Organization = {
        _type = `type`
        this
    }

    @QuerySqlField(name = "queryable", notNull = true)
    private var _queryable: Boolean = false
    def queryable: Boolean = _queryable
    def setQueryable: Organization = {
        _queryable = true
        this
    }
    def setUnqueryable: Organization = {
        _queryable = false
        this
    }

    private var _relatedProfiles: List[Profile] = List()
    def relatedProfiles: List[Profile] = _relatedProfiles
    def addRelatedProfile(profile: Profile): Organization = {
        _relatedProfiles ::= profile
        profile.setRelatedOrganization(this)
        this
    }
    def deleteRelatedProfile(profile: Profile): Organization = {
        _relatedProfiles = _relatedProfiles.filter(p => p.id != profile.id)
        this
    }

    // private var _relatedGroups: List[Group] = List()
    // def relatedGroups: List[Group] = _relatedGroups
    // def addRelatedGroup(group: Group): Organization = {
    //     _relatedGroups ::= group
    //     group.setRelatedOrganization(this)
    //     this
    // }
    // def deleteRelatedGroup(group: Group): Organization = {
    //     _relatedGroups = _relatedGroups.filter(g => g.id != group.id)
    //     this
    // }

    private var _defaultFileSystem: FileSystem = null
    def defaultFileSystem: FileSystem = _defaultFileSystem
    def setDefaultFileSystem(fileSystem: FileSystem): Future[Organization] = {
        _fileSystems.find(f => f.id == fileSystem.id) match {
            case Some(fs) => {
                _fileSystems = _fileSystems.filter(f => f.id != fs.id).::(_defaultFileSystem)
                _defaultFileSystem = fs
                Future.successful(this)
            }
            case None => Future.failed(new Error("The new default filesystem must be mounted first"))
        }
    }

    private var _fileSystems: List[FileSystem] = List()
    def fileSystems: List[FileSystem] = _fileSystems
    def addFileSystem(fileSystem: FileSystem): Organization = {
        _fileSystems ::= fileSystem
        fileSystem.addOrganization(this)
        this
    }
    def removeFileSystem(fileSystem: FileSystem)(implicit ec: ExecutionContext): Future[Organization] = {
        if (fileSystem.id == defaultFileSystem.id) return Future.failed(new Error("Can't unmount default filesystem"))
        Future({
            // TODO : verify unmount safety (settings or license files in current fileSystem)
        }).transformWith({
            case Success(value) => {
                _fileSystems = _fileSystems.filter(f => f.id != fileSystem.id)
                Future.successful(this)
            }
            case Failure(cause) => Future.failed(cause)
        })
    }

    // private var _applications: List[Application] = List()
    // def applications: List[Application] = _applications
    // def addApplication(application: Application): Organization = {
    //     _applications ::= application
    //     application.setRelatedOrganization(this)
    //     this
    // }
    // def deleteApplication(application: Application): Organization = {
    //     _applications = _applications.filter(a => a.id != application.id)
    //     this
    // }

    def persist(implicit ec: ExecutionContext): Future[Unit] = {
        store.persistOrganization(this)
    }

    def remove(implicit ec: ExecutionContext): Future[Unit] = {
        store.deleteOrganization(this)
    }
}