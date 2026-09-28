package com.linode.manager.data.remote

import com.google.gson.annotations.SerializedName

// ---------- generic ----------

data class PagedResponse<T>(
    val data: List<T> = emptyList(),
    val page: Int = 1,
    val pages: Int = 1,
    val results: Int = 0,
)

data class ApiErrorItem(
    val field: String? = null,
    val reason: String? = null,
)

data class ApiErrorResponse(
    val errors: List<ApiErrorItem> = emptyList(),
)

fun ApiErrorResponse.firstMessage(default: String = "Request failed"): String = errors.firstOrNull()?.reason ?: default

// ---------- linodes ----------

data class LinodeAlerts(
    val cpu: Int? = null,
    val io: Int? = null,
    @SerializedName("network_in") val networkIn: Int? = null,
    @SerializedName("network_out") val networkOut: Int? = null,
    @SerializedName("transfer_quota") val transferQuota: Int? = null,
)

data class BackupSchedule(
    val day: String? = null,
    val window: String? = null,
)

data class LinodeBackups(
    val available: Boolean? = null,
    val enabled: Boolean? = null,
    @SerializedName("last_successful") val lastSuccessful: String? = null,
    val schedule: BackupSchedule? = null,
)

data class LinodeSpecs(
    val disk: Int? = null,
    val memory: Int? = null,
    val vcpus: Int? = null,
    val transfer: Int? = null,
    val gpus: Int? = null,
)

data class LinodeInstance(
    val id: Int = 0,
    val label: String = "",
    val region: String? = null,
    val type: String? = null,
    val image: String? = null,
    val status: String? = null,
    val ipv4: List<String> = emptyList(),
    val ipv6: String? = null,
    val created: String? = null,
    val updated: String? = null,
    val tags: List<String> = emptyList(),
    val specs: LinodeSpecs? = null,
    val alerts: LinodeAlerts? = null,
    val backups: LinodeBackups? = null,
    @SerializedName("watchdog_enabled") val watchdogEnabled: Boolean? = null,
    @SerializedName("disk_encryption") val diskEncryption: String? = null,
    val hypervisor: String? = null,
)

data class LinodeTypePrice(
    val hourly: Double? = null,
    val monthly: Double? = null,
)

/** `addons.backups.price.{hourly,monthly}` in the API. */
data class LinodeTypeAddon(
    val price: LinodeTypePrice? = null,
)

data class LinodeTypeAddons(
    val backups: LinodeTypeAddon? = null,
)

data class LinodeType(
    val id: String = "",
    val label: String = "",
    val disk: Int? = null,
    val memory: Int? = null,
    val vcpus: Int? = null,
    val transfer: Int? = null,
    val price: LinodeTypePrice? = null,
    val addons: LinodeTypeAddons? = null,
    @SerializedName("class") val clazz: String? = null,
    val successor: String? = null,
)

data class Region(
    val id: String = "",
    val label: String = "",
    val country: String? = null,
    val capabilities: List<String> = emptyList(),
    val status: String? = null,
    @SerializedName("site_type") val siteType: String? = null,
)

data class Image(
    val id: String = "",
    val label: String = "",
    val description: String? = null,
    val vendor: String? = null,
    val deprecated: Boolean? = null,
    @SerializedName("is_public") val isPublic: Boolean? = null,
    val type: String? = null,
    val status: String? = null,
    val size: Long? = null,
    val created: String? = null,
)

data class LinodeStatsData(
    val cpu: List<List<Double>> = emptyList(),
    val io: LinodeIo? = null,
    val netv4: LinodeNet? = null,
    val netv6: LinodeNet? = null,
)

data class LinodeIo(
    val io: List<List<Double>> = emptyList(),
    val swap: List<List<Double>> = emptyList(),
)

data class LinodeNet(
    @SerializedName("in") val inbound: List<List<Double>> = emptyList(),
    val out: List<List<Double>> = emptyList(),
    @SerializedName("private_in") val privateIn: List<List<Double>>? = null,
    @SerializedName("private_out") val privateOut: List<List<Double>>? = null,
)

data class LinodeStats(
    val data: LinodeStatsData? = null,
    val title: String? = null,
)

data class LinodeDisk(
    val id: Int = 0,
    val label: String = "",
    val size: Int? = null,
    val filesystem: String? = null,
    val status: String? = null,
    val created: String? = null,
)

data class LinodeConfig(
    val id: Int = 0,
    val label: String = "",
    val kernel: String? = null,
    @SerializedName("memory_limit") val memoryLimit: Int? = null,
)

data class LinodeIpAddress(
    val address: String = "",
    val gateway: String? = null,
    val subnet_mask: String? = null,
    val prefix: Int? = null,
    val type: String? = null,
    val public: Boolean? = null,
    val rdns: String? = null,
    val linode_id: Int? = null,
    val region: String? = null,
)

data class NetworkingInfo(
    val ipv4: NetworkingV4? = null,
    val ipv6: NetworkingV6? = null,
)

data class NetworkingV6Slaac(
    val address: String? = null,
)

data class NetworkingV6(
    val slaac: NetworkingV6Slaac? = null,
    val link_local: NetworkingV6Slaac? = null,
)

data class NetworkingV4(
    val public: List<LinodeIpAddress> = emptyList(),
    val private: List<LinodeIpAddress> = emptyList(),
    val reserved: List<LinodeIpAddress> = emptyList(),
    val shared: List<LinodeIpAddress> = emptyList(),
)

// ---------- volumes ----------

data class Volume(
    val id: Int = 0,
    val label: String = "",
    val region: String? = null,
    val size: Int? = null,
    val status: String? = null,
    @SerializedName("linode_id") val linodeId: Int? = null,
    @SerializedName("linode_label") val linodeLabel: String? = null,
    @SerializedName("filesystem_path") val filesystemPath: String? = null,
    val encryption: String? = null,
    @SerializedName("hardware_type") val hardwareType: String? = null,
    val tags: List<String> = emptyList(),
    val created: String? = null,
    val updated: String? = null,
)

// ---------- firewalls ----------

data class FirewallRuleAddresses(
    val ipv4: List<String>? = null,
    val ipv6: List<String>? = null,
)

data class FirewallRule(
    val action: String? = null,
    val label: String? = null,
    val description: String? = null,
    val protocol: String? = null,
    val ports: String? = null,
    val addresses: FirewallRuleAddresses? = null,
)

data class FirewallRules(
    @SerializedName("inbound_policy") val inboundPolicy: String? = null,
    @SerializedName("outbound_policy") val outboundPolicy: String? = null,
    val inbound: List<FirewallRule> = emptyList(),
    val outbound: List<FirewallRule> = emptyList(),
    val fingerprint: String? = null,
    val version: Int? = null,
)

data class FirewallDeviceEntity(
    val id: Int? = null,
    val label: String? = null,
    val type: String? = null,
    val url: String? = null,
)

data class Firewall(
    val id: Int = 0,
    val label: String = "",
    val status: String? = null,
    val rules: FirewallRules? = null,
    val entities: List<FirewallDeviceEntity> = emptyList(),
    val tags: List<String> = emptyList(),
    val created: String? = null,
    val updated: String? = null,
)

// ---------- domains ----------

data class Domain(
    val id: Int = 0,
    val domain: String = "",
    val type: String? = null,
    val status: String? = null,
    val description: String? = null,
    @SerializedName("soa_email") val soaEmail: String? = null,
    @SerializedName("ttl_sec") val ttlSec: Int? = null,
    @SerializedName("refresh_sec") val refreshSec: Int? = null,
    @SerializedName("retry_sec") val retrySec: Int? = null,
    @SerializedName("expire_sec") val expireSec: Int? = null,
    val tags: List<String> = emptyList(),
)

data class DomainRecord(
    val id: Int = 0,
    val type: String? = null,
    val name: String? = null,
    val target: String? = null,
    val priority: Int? = null,
    val weight: Int? = null,
    val port: Int? = null,
    val service: String? = null,
    val protocol: String? = null,
    val ttl_sec: Int? = null,
)

// ---------- nodebalancers / lke ----------

data class NodeBalancer(
    val id: Int = 0,
    val label: String? = null,
    val region: String? = null,
    val hostname: String? = null,
    val ipv4: String? = null,
    val ipv6: String? = null,
    @SerializedName("client_conn_throttle") val clientConnThrottle: Int? = null,
    val tags: List<String> = emptyList(),
    val created: String? = null,
    val updated: String? = null,
)

data class LkeCluster(
    val id: Int = 0,
    val label: String = "",
    val region: String? = null,
    @SerializedName("k8s_version") val k8sVersion: String? = null,
    val status: String? = null,
    val created: String? = null,
    val updated: String? = null,
    val tags: List<String> = emptyList(),
)

// ---------- account ----------

data class Profile(
    val username: String = "",
    val email: String? = null,
    @SerializedName("first_name") val firstName: String? = null,
    @SerializedName("last_name") val lastName: String? = null,
    val timezone: String? = null,
    @SerializedName("email_notifications") val emailNotifications: Boolean? = null,
    @SerializedName("authorized_keys") val authorizedKeys: List<String>? = null,
    val restricted: Boolean? = null,
)

data class Account(
    val email: String? = null,
    @SerializedName("first_name") val firstName: String? = null,
    @SerializedName("last_name") val lastName: String? = null,
    val company: String? = null,
    val address_1: String? = null,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    val balance: Double? = null,
    @SerializedName("balance_uninvoiced") val balanceUninvoiced: Double? = null,
    val currency: String? = null,
    val tax_id: String? = null,
    val capabilities: List<String> = emptyList(),
)

data class Notification(
    val label: String? = null,
    val message: String? = null,
    val severity: String? = null,
    val type: String? = null,
    val until: String? = null,
    val body: String? = null,
    val entity: NotificationEntity? = null,
)

data class NotificationEntity(
    val id: Int? = null,
    val label: String? = null,
    val type: String? = null,
    val url: String? = null,
)

data class LinodeEvent(
    val id: Int = 0,
    val action: String? = null,
    val status: String? = null,
    val created: String? = null,
    val updated: String? = null,
    val seen: Boolean? = null,
    val read: Boolean? = null,
    val username: String? = null,
    val entity: NotificationEntity? = null,
    val message: String? = null,
    val percent_complete: Int? = null,
    val duration: Double? = null,
)

data class Invoice(
    val id: Int = 0,
    val label: String? = null,
    val total: Double? = null,
    val date: String? = null,
    val status: String? = null,
)

data class TransferUsage(
    val billable: Long? = null,
    val quota: Long? = null,
    val used: Long? = null,
    val region_transfers: List<RegionTransfer>? = null,
)

data class RegionTransfer(
    val billable: Long? = null,
    val quota: Long? = null,
    val used: Long? = null,
    val region: String? = null,
)

data class SshKey(
    val id: Int = 0,
    val label: String = "",
    val ssh_key: String? = null,
    val created: String? = null,
)

data class SupportTicket(
    val id: Int = 0,
    val summary: String? = null,
    val description: String? = null,
    val status: String? = null,
    val opened: String? = null,
    val updated: String? = null,
    val closed: String? = null,
    val closable: Boolean? = null,
)

// ---------- request bodies ----------

data class CreateLinodeRequest(
    val label: String? = null,
    val region: String,
    val type: String,
    val image: String? = null,
    @SerializedName("root_pass") val rootPass: String? = null,
    @SerializedName("authorized_keys") val authorizedKeys: List<String>? = null,
    val tags: List<String>? = null,
    val backups_enabled: Boolean? = null,
    @SerializedName("private_ip") val privateIp: Boolean? = null,
)

data class ResizeRequest(
    val type: String,
    @SerializedName("allow_auto_disk_resize") val allowAutoDiskResize: Boolean = true,
)

data class RebuildRequest(
    val image: String,
    @SerializedName("root_pass") val rootPass: String,
    @SerializedName("authorized_keys") val authorizedKeys: List<String>? = null,
)

data class CloneRequest(
    val label: String? = null,
    val region: String? = null,
    val type: String? = null,
    val linode_id: Int? = null,
)

data class PasswordRequest(
    @SerializedName("root_pass") val rootPass: String,
)

data class MigrateRequest(
    val region: String,
)

data class LabelUpdate(
    val label: String? = null,
    val tags: List<String>? = null,
    @SerializedName("watchdog_enabled") val watchdogEnabled: Boolean? = null,
)

data class CreateVolumeRequest(
    val label: String,
    val region: String,
    val size: Int,
    val linode_id: Int? = null,
)

data class AttachVolumeRequest(
    @SerializedName("linode_id") val linodeId: Int,
)

data class ResizeVolumeRequest(
    val size: Int,
)

data class CreateDomainRequest(
    val domain: String,
    val type: String = "master",
    @SerializedName("soa_email") val soaEmail: String,
    val description: String? = null,
)

data class CreateDomainRecordRequest(
    val type: String,
    val name: String,
    val target: String,
    val priority: Int? = null,
    @SerializedName("ttl_sec") val ttlSec: Int? = null,
)

data class CreateFirewallRequest(
    val label: String,
    val rules: FirewallRules? = null,
    val tags: List<String>? = null,
)

data class FirewallUpdateRequest(
    val label: String? = null,
    val status: String? = null,
    val tags: List<String>? = null,
)

data class FirewallRulesUpdate(
    @SerializedName("inbound_policy") val inboundPolicy: String? = null,
    @SerializedName("outbound_policy") val outboundPolicy: String? = null,
    val inbound: List<FirewallRule>? = null,
    val outbound: List<FirewallRule>? = null,
)

data class FirewallDevice(
    val id: Int = 0,
    val created: String? = null,
    val updated: String? = null,
    val entity: FirewallDeviceEntity? = null,
)

data class FirewallDeviceRequest(
    val id: Int,
    val type: String,
)

data class CreateSshKeyRequest(
    val label: String,
    @SerializedName("ssh_key") val sshKey: String,
)

data class CreateTicketRequest(
    val summary: String,
    val description: String,
)
