package com.linode.manager.data.repository

import com.linode.manager.data.remote.AttachVolumeRequest
import com.linode.manager.data.remote.CloneRequest
import com.linode.manager.data.remote.CreateDomainRecordRequest
import com.linode.manager.data.remote.CreateDomainRequest
import com.linode.manager.data.remote.CreateFirewallRequest
import com.linode.manager.data.remote.CreateLinodeRequest
import com.linode.manager.data.remote.CreateSshKeyRequest
import com.linode.manager.data.remote.CreateTicketRequest
import com.linode.manager.data.remote.CreateVolumeRequest
import com.linode.manager.data.remote.FirewallDeviceRequest
import com.linode.manager.data.remote.FirewallRulesUpdate
import com.linode.manager.data.remote.FirewallUpdateRequest
import com.linode.manager.data.remote.LabelUpdate
import com.linode.manager.data.remote.LinodeApi
import com.linode.manager.data.remote.MigrateRequest
import com.linode.manager.data.remote.PasswordRequest
import com.linode.manager.data.remote.RebuildRequest
import com.linode.manager.data.remote.ResizeRequest
import com.linode.manager.data.remote.ResizeVolumeRequest

class LinodeRepository(
    private val api: LinodeApi,
) {
    suspend fun validateToken() = api.getProfile().toResult()

    suspend fun profile() = api.getProfile().toResult()

    suspend fun account() = api.getAccount().toResult()

    suspend fun notifications() = api.getNotifications().toResult()

    suspend fun events(page: Int = 1) = api.getEvents(page).toResult()

    suspend fun markEventSeen(id: Int) = api.markEventSeen(id).toUnitResult()

    suspend fun invoices() = api.getInvoices().toResult()

    suspend fun transfer() = api.getTransfer().toResult()

    suspend fun sshKeys() = api.getSshKeys().toResult()

    suspend fun createSshKey(
        label: String,
        key: String,
    ) = api.createSshKey(CreateSshKeyRequest(label, key)).toResult()

    suspend fun deleteSshKey(id: Int) = api.deleteSshKey(id).toUnitResult()

    suspend fun linodes() = api.listLinodes().toResult()

    suspend fun linode(id: Int) = api.getLinode(id).toResult()

    suspend fun createLinode(req: CreateLinodeRequest) = api.createLinode(req).toResult()

    suspend fun updateLinode(
        id: Int,
        req: LabelUpdate,
    ) = api.updateLinode(id, req).toResult()

    suspend fun deleteLinode(id: Int) = api.deleteLinode(id).toUnitResult()

    suspend fun boot(id: Int) = api.bootLinode(id).toUnitResult()

    suspend fun reboot(id: Int) = api.rebootLinode(id).toUnitResult()

    suspend fun shutdown(id: Int) = api.shutdownLinode(id).toUnitResult()

    suspend fun resize(
        id: Int,
        type: String,
    ) = api.resizeLinode(id, ResizeRequest(type)).toUnitResult()

    suspend fun rebuild(
        id: Int,
        image: String,
        rootPass: String,
    ) = api.rebuildLinode(id, RebuildRequest(image, rootPass)).toResult()

    suspend fun clone(
        id: Int,
        req: CloneRequest,
    ) = api.cloneLinode(id, req).toResult()

    suspend fun resetPassword(
        id: Int,
        pass: String,
    ) = api.resetPassword(id, PasswordRequest(pass)).toUnitResult()

    suspend fun migrate(
        id: Int,
        region: String,
    ) = api.migrateLinode(id, MigrateRequest(region)).toUnitResult()

    suspend fun enableBackups(id: Int) = api.enableBackups(id).toUnitResult()

    suspend fun snapshot(id: Int) = api.takeSnapshot(id).toUnitResult()

    suspend fun disks(id: Int) = api.listDisks(id).toResult()

    suspend fun configs(id: Int) = api.listConfigs(id).toResult()

    suspend fun networking(id: Int) = api.getNetworking(id).toResult()

    suspend fun linodeVolumes(id: Int) = api.listLinodeVolumes(id).toResult()

    suspend fun linodeFirewalls(id: Int) = api.listLinodeFirewalls(id).toResult()

    suspend fun stats(id: Int) = api.getStats(id).toResult()

    suspend fun linodeTransfer(id: Int) = api.getLinodeTransfer(id).toResult()

    suspend fun types() = api.listTypes().toResult()

    suspend fun regions() = api.listRegions().toResult()

    suspend fun images() = api.listImages().toResult()

    suspend fun volumes() = api.listVolumes().toResult()

    suspend fun createVolume(
        label: String,
        region: String,
        size: Int,
        linodeId: Int?,
    ) = api.createVolume(CreateVolumeRequest(label, region, size, linodeId)).toResult()

    suspend fun deleteVolume(id: Int) = api.deleteVolume(id).toUnitResult()

    suspend fun attachVolume(
        id: Int,
        linodeId: Int,
    ) = api.attachVolume(id, AttachVolumeRequest(linodeId)).toResult()

    suspend fun detachVolume(id: Int) = api.detachVolume(id).toUnitResult()

    suspend fun resizeVolume(
        id: Int,
        size: Int,
    ) = api.resizeVolume(id, ResizeVolumeRequest(size)).toUnitResult()

    suspend fun firewalls() = api.listFirewalls().toResult()

    suspend fun createFirewall(label: String) = api.createFirewall(CreateFirewallRequest(label)).toResult()

    suspend fun firewall(id: Int) = api.getFirewall(id).toResult()

    suspend fun updateFirewall(
        id: Int,
        label: String? = null,
        status: String? = null,
    ) = api.updateFirewall(id, FirewallUpdateRequest(label, status)).toResult()

    suspend fun deleteFirewall(id: Int) = api.deleteFirewall(id).toUnitResult()

    suspend fun updateFirewallRules(
        id: Int,
        body: FirewallRulesUpdate,
    ) = api.updateFirewallRules(id, body).toResult()

    suspend fun firewallDevices(id: Int) = api.listFirewallDevices(id).toResult()

    suspend fun attachFirewallDevice(
        id: Int,
        entityId: Int,
        type: String,
    ) = api.createFirewallDevice(id, FirewallDeviceRequest(entityId, type)).toResult()

    suspend fun detachFirewallDevice(
        firewallId: Int,
        deviceId: Int,
    ) = api.deleteFirewallDevice(firewallId, deviceId).toUnitResult()

    suspend fun domains() = api.listDomains().toResult()

    suspend fun createDomain(
        domain: String,
        soaEmail: String,
    ) = api.createDomain(CreateDomainRequest(domain, "master", soaEmail)).toResult()

    suspend fun domain(id: Int) = api.getDomain(id).toResult()

    suspend fun deleteDomain(id: Int) = api.deleteDomain(id).toUnitResult()

    suspend fun domainRecords(id: Int) = api.listDomainRecords(id).toResult()

    suspend fun createDomainRecord(
        id: Int,
        type: String,
        name: String,
        target: String,
    ) = api.createDomainRecord(id, CreateDomainRecordRequest(type, name, target)).toResult()

    suspend fun deleteDomainRecord(
        domainId: Int,
        recordId: Int,
    ) = api.deleteDomainRecord(domainId, recordId).toUnitResult()

    suspend fun nodeBalancers() = api.listNodeBalancers().toResult()

    suspend fun deleteNodeBalancer(id: Int) = api.deleteNodeBalancer(id).toUnitResult()

    suspend fun lkeClusters() = api.listLkeClusters().toResult()

    suspend fun deleteLkeCluster(id: Int) = api.deleteLkeCluster(id).toUnitResult()

    suspend fun tickets() = api.listTickets().toResult()

    suspend fun createTicket(
        summary: String,
        description: String,
    ) = api.createTicket(CreateTicketRequest(summary, description)).toResult()
}
