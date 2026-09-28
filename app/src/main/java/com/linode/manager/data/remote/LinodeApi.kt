package com.linode.manager.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import kotlin.jvm.JvmSuppressWildcards

interface LinodeApi {
    // ---- auth check / profile ----
    @GET("profile")
    suspend fun getProfile(): Response<Profile>

    @GET("account")
    suspend fun getAccount(): Response<Account>

    @GET("account/notifications")
    suspend fun getNotifications(): Response<PagedResponse<Notification>>

    @GET("account/events")
    suspend fun getEvents(
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 50,
    ): Response<PagedResponse<LinodeEvent>>

    @POST("account/events/{eventId}/seen")
    suspend fun markEventSeen(
        @Path("eventId") eventId: Int,
    ): Response<Unit>

    @GET("account/invoices")
    suspend fun getInvoices(
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 25,
    ): Response<PagedResponse<Invoice>>

    @GET("account/transfer")
    suspend fun getTransfer(): Response<TransferUsage>

    @GET("profile/sshkeys")
    suspend fun getSshKeys(): Response<PagedResponse<SshKey>>

    @POST("profile/sshkeys")
    suspend fun createSshKey(
        @Body body: CreateSshKeyRequest,
    ): Response<SshKey>

    @DELETE("profile/sshkeys/{keyId}")
    suspend fun deleteSshKey(
        @Path("keyId") keyId: Int,
    ): Response<Unit>

    // ---- linodes ----
    @GET("linode/instances")
    suspend fun listLinodes(
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 100,
    ): Response<PagedResponse<LinodeInstance>>

    @POST("linode/instances")
    suspend fun createLinode(
        @Body body: CreateLinodeRequest,
    ): Response<LinodeInstance>

    @GET("linode/instances/{id}")
    suspend fun getLinode(
        @Path("id") id: Int,
    ): Response<LinodeInstance>

    @PUT("linode/instances/{id}")
    suspend fun updateLinode(
        @Path("id") id: Int,
        @Body body: LabelUpdate,
    ): Response<LinodeInstance>

    @DELETE("linode/instances/{id}")
    suspend fun deleteLinode(
        @Path("id") id: Int,
    ): Response<Unit>

    @POST("linode/instances/{id}/boot")
    suspend fun bootLinode(
        @Path("id") id: Int,
        @Body body: Map<String, String> = emptyMap(),
    ): Response<Unit>

    @POST("linode/instances/{id}/reboot")
    suspend fun rebootLinode(
        @Path("id") id: Int,
        @Body body: Map<String, String> = emptyMap(),
    ): Response<Unit>

    @POST("linode/instances/{id}/shutdown")
    suspend fun shutdownLinode(
        @Path("id") id: Int,
        @Body body: Map<String, String> = emptyMap(),
    ): Response<Unit>

    @POST("linode/instances/{id}/resize")
    suspend fun resizeLinode(
        @Path("id") id: Int,
        @Body body: ResizeRequest,
    ): Response<Unit>

    @POST("linode/instances/{id}/rebuild")
    suspend fun rebuildLinode(
        @Path("id") id: Int,
        @Body body: RebuildRequest,
    ): Response<LinodeInstance>

    @POST("linode/instances/{id}/clone")
    suspend fun cloneLinode(
        @Path("id") id: Int,
        @Body body: CloneRequest,
    ): Response<LinodeInstance>

    @POST("linode/instances/{id}/password")
    suspend fun resetPassword(
        @Path("id") id: Int,
        @Body body: PasswordRequest,
    ): Response<Unit>

    @POST("linode/instances/{id}/rescue")
    suspend fun rescueLinode(
        @Path("id") id: Int,
        @Body body: Map<String, @JvmSuppressWildcards Any> = emptyMap(),
    ): Response<Unit>

    @POST("linode/instances/{id}/migrate")
    suspend fun migrateLinode(
        @Path("id") id: Int,
        @Body body: MigrateRequest,
    ): Response<Unit>

    @POST("linode/instances/{id}/backups/enable")
    suspend fun enableBackups(
        @Path("id") id: Int,
    ): Response<Unit>

    @POST("linode/instances/{id}/backups")
    suspend fun takeSnapshot(
        @Path("id") id: Int,
        @Body body: Map<String, String> = emptyMap(),
    ): Response<Unit>

    @GET("linode/instances/{id}/disks")
    suspend fun listDisks(
        @Path("id") id: Int,
    ): Response<PagedResponse<LinodeDisk>>

    @GET("linode/instances/{id}/configs")
    suspend fun listConfigs(
        @Path("id") id: Int,
    ): Response<PagedResponse<LinodeConfig>>

    @GET("linode/instances/{id}/ips")
    suspend fun getNetworking(
        @Path("id") id: Int,
    ): Response<NetworkingInfo>

    @GET("linode/instances/{id}/volumes")
    suspend fun listLinodeVolumes(
        @Path("id") id: Int,
    ): Response<PagedResponse<Volume>>

    @GET("linode/instances/{id}/firewalls")
    suspend fun listLinodeFirewalls(
        @Path("id") id: Int,
    ): Response<PagedResponse<Firewall>>

    @GET("linode/instances/{id}/stats")
    suspend fun getStats(
        @Path("id") id: Int,
    ): Response<LinodeStats>

    @GET("linode/instances/{id}/transfer")
    suspend fun getLinodeTransfer(
        @Path("id") id: Int,
    ): Response<TransferUsage>

    // ---- catalogue ----
    @GET("linode/types")
    suspend fun listTypes(
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 100,
    ): Response<PagedResponse<LinodeType>>

    @GET("regions")
    suspend fun listRegions(
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 100,
    ): Response<PagedResponse<Region>>

    @GET("images")
    suspend fun listImages(
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 100,
    ): Response<PagedResponse<Image>>

    // ---- volumes ----
    @GET("volumes")
    suspend fun listVolumes(
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 100,
    ): Response<PagedResponse<Volume>>

    @POST("volumes")
    suspend fun createVolume(
        @Body body: CreateVolumeRequest,
    ): Response<Volume>

    @DELETE("volumes/{id}")
    suspend fun deleteVolume(
        @Path("id") id: Int,
    ): Response<Unit>

    @POST("volumes/{id}/attach")
    suspend fun attachVolume(
        @Path("id") id: Int,
        @Body body: AttachVolumeRequest,
    ): Response<Volume>

    @POST("volumes/{id}/detach")
    suspend fun detachVolume(
        @Path("id") id: Int,
    ): Response<Unit>

    @POST("volumes/{id}/resize")
    suspend fun resizeVolume(
        @Path("id") id: Int,
        @Body body: ResizeVolumeRequest,
    ): Response<Unit>

    // ---- firewalls ----
    @GET("networking/firewalls")
    suspend fun listFirewalls(
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 100,
    ): Response<PagedResponse<Firewall>>

    @POST("networking/firewalls")
    suspend fun createFirewall(
        @Body body: CreateFirewallRequest,
    ): Response<Firewall>

    @GET("networking/firewalls/{id}")
    suspend fun getFirewall(
        @Path("id") id: Int,
    ): Response<Firewall>

    @PUT("networking/firewalls/{id}")
    suspend fun updateFirewall(
        @Path("id") id: Int,
        @Body body: FirewallUpdateRequest,
    ): Response<Firewall>

    @DELETE("networking/firewalls/{id}")
    suspend fun deleteFirewall(
        @Path("id") id: Int,
    ): Response<Unit>

    @PUT("networking/firewalls/{id}/rules")
    suspend fun updateFirewallRules(
        @Path("id") id: Int,
        @Body body: FirewallRulesUpdate,
    ): Response<FirewallRules>

    @GET("networking/firewalls/{id}/devices")
    suspend fun listFirewallDevices(
        @Path("id") id: Int,
    ): Response<PagedResponse<FirewallDevice>>

    @POST("networking/firewalls/{id}/devices")
    suspend fun createFirewallDevice(
        @Path("id") id: Int,
        @Body body: FirewallDeviceRequest,
    ): Response<FirewallDevice>

    @DELETE("networking/firewalls/{firewallId}/devices/{deviceId}")
    suspend fun deleteFirewallDevice(
        @Path("firewallId") firewallId: Int,
        @Path("deviceId") deviceId: Int,
    ): Response<Unit>

    // ---- domains ----
    @GET("domains")
    suspend fun listDomains(
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 100,
    ): Response<PagedResponse<Domain>>

    @POST("domains")
    suspend fun createDomain(
        @Body body: CreateDomainRequest,
    ): Response<Domain>

    @GET("domains/{id}")
    suspend fun getDomain(
        @Path("id") id: Int,
    ): Response<Domain>

    @DELETE("domains/{id}")
    suspend fun deleteDomain(
        @Path("id") id: Int,
    ): Response<Unit>

    @GET("domains/{id}/records")
    suspend fun listDomainRecords(
        @Path("id") id: Int,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 100,
    ): Response<PagedResponse<DomainRecord>>

    @POST("domains/{id}/records")
    suspend fun createDomainRecord(
        @Path("id") id: Int,
        @Body body: CreateDomainRecordRequest,
    ): Response<DomainRecord>

    @DELETE("domains/{domainId}/records/{recordId}")
    suspend fun deleteDomainRecord(
        @Path("domainId") domainId: Int,
        @Path("recordId") recordId: Int,
    ): Response<Unit>

    // ---- nodebalancers / lke ----
    @GET("nodebalancers")
    suspend fun listNodeBalancers(
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 100,
    ): Response<PagedResponse<NodeBalancer>>

    @DELETE("nodebalancers/{id}")
    suspend fun deleteNodeBalancer(
        @Path("id") id: Int,
    ): Response<Unit>

    @GET("lke/clusters")
    suspend fun listLkeClusters(
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 100,
    ): Response<PagedResponse<LkeCluster>>

    @DELETE("lke/clusters/{id}")
    suspend fun deleteLkeCluster(
        @Path("id") id: Int,
    ): Response<Unit>

    // ---- support ----
    @GET("support/tickets")
    suspend fun listTickets(
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 50,
    ): Response<PagedResponse<SupportTicket>>

    @POST("support/tickets")
    suspend fun createTicket(
        @Body body: CreateTicketRequest,
    ): Response<SupportTicket>
}
