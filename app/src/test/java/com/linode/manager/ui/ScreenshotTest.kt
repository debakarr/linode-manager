package com.linode.manager.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.linode.manager.AppContainer
import com.linode.manager.data.ThemeMode
import com.linode.manager.ui.theme.LinodeManagerTheme
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every screen on the JVM against a mock Linode API and saves
 * screenshots (build/screenshots). Catches composition crashes and makes
 * layout regressions on small phones and tablets visible without a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
@OptIn(ExperimentalTestApi::class)
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val server = MockWebServer()
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        server.dispatcher = FakeLinodeApi
        server.start()
        container = AppContainer(ApplicationProvider.getApplicationContext(), server.url("/v4/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    private fun waitForText(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun signedIn(theme: ThemeMode = ThemeMode.LIGHT) {
        container.tokenStore.saveToken("a".repeat(64))
        container.settings.updateThemeMode(theme)
        compose.setContent { LinodeManagerTheme(mode = container.settings.themeMode) { LinodeRoot(container) } }
        waitForText("Hello, demo")
    }

    private fun tab(label: String) {
        compose.onNodeWithContentDescription(label, useUnmergedTree = true).performClick()
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun loginPhone() {
        container.tokenStore.clear()
        compose.setContent { LinodeManagerTheme(mode = ThemeMode.LIGHT) { LinodeRoot(container) } }
        waitForText("Sign in")
        shot("01-login")
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun mainFlowPhone() {
        signedIn()
        waitForText("web-01")
        shot("02-dashboard")

        tab("Linodes")
        waitForText("db-primary")
        shot("03-linodes")

        compose.onNodeWithText("web-01").performClick()
        waitForText("g6-standard-2")
        shot("04-linode-overview")
        compose.onNodeWithText("Network").performClick()
        shot("05-linode-network")
        compose.onNodeWithText("Metrics").performClick()
        waitForText("Peak")
        shot("06-linode-metrics")
        compose.onNodeWithText("Manage").performClick()
        shot("07-linode-manage")

        compose.onNodeWithText("Open terminal").performClick()
        waitForText("Connect")
        shot("08-ssh-setup")
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun otherTabsPhone() {
        signedIn()
        tab("Volumes")
        waitForText("backups-vol")
        shot("09-volumes")

        tab("Network")
        waitForText("web-fw")
        shot("10-network-firewalls")
        compose.onNodeWithText("Domains").performClick()
        waitForText("example.com")
        shot("11-network-domains")
        compose.onNodeWithText("Firewalls").performClick()
        compose.onNodeWithText("web-fw").performClick()
        waitForText("Default policies")
        shot("12-firewall-detail")
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun accountAndCreatePhone() {
        signedIn()
        tab("Account")
        waitForText("Billing")
        shot("13-account")

        tab("Linodes")
        waitForText("db-primary")
        compose.onNodeWithText("Create", useUnmergedTree = true).performClick()
        waitForText("Location & image")
        shot("14-create-linode")

        compose.onNodeWithContentDescription("Back").performClick()
        tab("Home")
        waitForText("View all")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Recent activity"))
        compose.onAllNodesWithText("View all").onLast().performClick()
        waitForText("Activity")
        shot("15-activity")
    }

    /**
     * Regression for the v2.0.0 crash: device keys stored by v1.4.1 used
     * R8-obfuscated field names. Account and SSH setup must render them.
     */
    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun upgradeFromV1DeviceKeys() {
        val key =
            com.linode.manager.data.SshKeyGen
                .generateRsa("pixel-ssh")
        val legacy = """[{"a":"pixel-ssh","b":"${key.publicKey}","c":${com.google.gson.Gson().toJson(
            key.privatePem,
        )},"d":"${key.fingerprint}","e":1757000000000}]"""
        ApplicationProvider
            .getApplicationContext<android.content.Context>()
            .getSharedPreferences("linode_device_keys_fallback", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("device_ssh_keys", legacy)
            .commit()
        signedIn()
        tab("Account")
        waitForText("pixel-ssh")
        shot("22-account-with-v1-keys")
        tab("Linodes")
        waitForText("db-primary")
        compose.onNodeWithText("web-01").performClick()
        waitForText("g6-standard-2")
        compose.onNodeWithText("Open terminal").performClick()
        waitForText("Connect")
        waitForText("pixel-ssh")
        shot("23-ssh-setup-with-v1-keys")
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun darkDashboardPhone() {
        signedIn(ThemeMode.DARK)
        waitForText("web-01")
        shot("16-dashboard-dark")
        tab("Linodes")
        waitForText("db-primary")
        shot("17-linodes-dark")
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-xhdpi")
    fun smallPhone() {
        signedIn()
        waitForText("web-01")
        shot("18-dashboard-small-phone")
        tab("Linodes")
        waitForText("db-primary")
        compose.onNodeWithText("web-01").performClick()
        waitForText("g6-standard-2")
        shot("19-linode-small-phone")
    }

    @Test
    @Config(qualifiers = "w900dp-h600dp-land-xhdpi")
    fun tabletLandscape() {
        signedIn()
        waitForText("web-01")
        shot("20-dashboard-tablet")
        tab("Linodes")
        waitForText("db-primary")
        shot("21-linodes-tablet")
    }
}

/** Minimal, realistic Linode API v4 responses for the screens under test. */
private object FakeLinodeApi : Dispatcher() {
    private fun page(items: String): String {
        val n =
            com.google.gson.JsonParser
                .parseString("[$items]")
                .asJsonArray
                .size()
        return """{"data":[$items],"page":1,"pages":1,"results":$n}"""
    }

    private fun series(
        n: Int,
        f: (Int) -> Double,
    ): String {
        val now = 1_790_000_000_000L
        return (0 until n).joinToString(",", "[", "]") { i -> "[${now - (n - i) * 300_000L},${f(i)}]" }
    }

    private val web01 =
        """
        {"id":101,"label":"web-01","region":"us-east","type":"g6-standard-2","image":"linode/ubuntu24.04","status":"running",
         "ipv4":["172.105.10.20","192.168.140.3"],"ipv6":"2600:3c03::f03c:95ff:fe12:3456/128","created":"2026-05-02T10:11:12",
         "tags":["prod","web"],"specs":{"disk":81920,"memory":4096,"vcpus":2,"transfer":4000},
         "backups":{"enabled":true,"last_successful":"2026-09-26T03:00:00"},"watchdog_enabled":true,"disk_encryption":"enabled"}
        """.trimIndent()

    private val linodes =
        web01 +
            """
            ,
            {"id":102,"label":"db-primary","region":"eu-central","type":"g6-dedicated-4","image":"linode/debian12","status":"running",
             "ipv4":["139.162.1.2"],"created":"2026-03-01T08:00:00","tags":["prod","db","postgres"],
             "specs":{"disk":163840,"memory":8192,"vcpus":4,"transfer":5000},"backups":{"enabled":false}},
            {"id":103,"label":"staging-worker-with-a-rather-long-label","region":"ap-south","type":"g6-nanode-1","image":"linode/alpine3.20","status":"offline",
             "ipv4":["45.79.1.9"],"created":"2026-08-20T08:00:00","tags":[],"specs":{"disk":25600,"memory":1024,"vcpus":1,"transfer":1000}},
            {"id":104,"label":"ci-runner","region":"us-west","type":"g6-standard-4","status":"provisioning","ipv4":["50.116.2.3"],
             "specs":{"disk":163840,"memory":8192,"vcpus":4,"transfer":5000}}
            """.trimIndent()

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.requestUrl?.encodedPath?.removePrefix("/v4/") ?: ""
        val body =
            when {
                path == "profile" -> """{"username":"demo","email":"ops@example.com","timezone":"UTC","restricted":false}"""
                path == "account" ->
                    """{"email":"ops@example.com","company":"Example Ltd","balance":12.5,""" +
                        """"balance_uninvoiced":7.42,"currency":"USD"}"""
                path == "account/notifications" ->
                    page(
                        """{"label":"Scheduled maintenance","message":"web-01 will be migrated on Oct 3 between 02:00–04:00 UTC.","severity":"minor","type":"maintenance"}""",
                    )
                path == "account/events" ->
                    page(
                        (1..12).joinToString(",") { i ->
                            val (a, st) =
                                listOf(
                                    "linode_boot" to "finished",
                                    "linode_snapshot" to "started",
                                    "volume_attach" to "finished",
                                    "firewall_update" to "finished",
                                )[i % 4]
                            """{"id":$i,"action":"$a","status":"$st","created":"2026-09-2${i % 7}T1$i:00:00","username":"demo","entity":{"label":"web-01"},"percent_complete":${if (st == "started") 40 else 100}}"""
                        },
                    )
                path == "account/transfer" -> """{"used":1240,"quota":12000,"billable":0}"""
                path == "account/invoices" ->
                    page(
                        """{"id":9001,"label":"Invoice","total":24.0,"date":"2026-09-01T00:00:00","status":"paid"},{"id":9000,"label":"Invoice","total":22.5,"date":"2026-08-01T00:00:00","status":"paid"}""",
                    )
                path == "profile/sshkeys" ->
                    page(
                        """{"id":1,"label":"laptop","ssh_key":"ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIPaJgSs0RfcY8M3m6tr3JrWUdz7UPeq3oKc8M6qLv0nK me@laptop"}""",
                    )
                path == "support/tickets" ->
                    page(
                        """{"id":555,"summary":"Reverse DNS for 172.105.10.20","status":"open","opened":"2026-09-20T10:00:00","updated":"2026-09-25T10:00:00"}""",
                    )
                path == "linode/instances" -> page(linodes)
                path.matches(Regex("linode/instances/\\d+")) -> web01
                path.endsWith(
                    "/ips",
                ) ->
                    """{"ipv4":{"public":[{"address":"172.105.10.20","rdns":"web-01.example.com","type":"ipv4"}],""" +
                        """"private":[{"address":"192.168.140.3","type":"ipv4"}]},"ipv6":{"slaac":""" +
                        """{"address":"2600:3c03::f03c:95ff:fe12:3456"},"link_local":{"address":"fe80::f03c:95ff:fe12:3456"}}}"""
                path.endsWith(
                    "/disks",
                ) ->
                    page(
                        """{"id":1,"label":"Ubuntu 24.04 Disk","size":79872,"filesystem":"ext4","status":"ready"},{"id":2,"label":"512 MB Swap Image","size":512,"filesystem":"swap","status":"ready"}""",
                    )
                path.endsWith("/configs") -> page("""{"id":1,"label":"My Ubuntu Profile","kernel":"linode/grub2"}""")
                path.matches(
                    Regex("linode/instances/\\d+/volumes"),
                ) -> page("""{"id":7,"label":"backups-vol","size":100,"status":"active","region":"us-east"}""")
                path.matches(Regex("linode/instances/\\d+/firewalls")) -> page("""{"id":11,"label":"web-fw","status":"enabled"}""")
                path.endsWith(
                    "/stats",
                ) -> """{"data":{"cpu":${series(
                    288,
                ) {
                    20 + 15 *
                        Math.sin(
                            it / 20.0,
                        ) + (it % 7)
                }},"io":{"io":${series(288) { 3 + (it % 11).toDouble() }},"swap":[]},"netv4":{"in":${series(288) {
                    150_000 + 80_000 *
                        Math.cos(
                            it / 30.0,
                        )
                }},"out":${series(288) { 400_000 + 200_000 * Math.sin(it / 25.0) }}}}}"""
                path == "volumes" ->
                    page(
                        """{"id":7,"label":"backups-vol","size":100,"status":"active","region":"us-east","linode_id":101,"linode_label":"web-01","hardware_type":"nvme","encryption":"enabled"},
                   {"id":8,"label":"scratch","size":20,"status":"active","region":"eu-central","hardware_type":"nvme"}""",
                    )
                path == "networking/firewalls" ->
                    page(
                        """{"id":11,"label":"web-fw","status":"enabled","rules":{"inbound_policy":"DROP","outbound_policy":"ACCEPT","inbound":[{"action":"ACCEPT","label":"ssh","protocol":"TCP","ports":"22","addresses":{"ipv4":["0.0.0.0/0"]}},{"action":"ACCEPT","label":"https","protocol":"TCP","ports":"443","addresses":{"ipv4":["0.0.0.0/0"],"ipv6":["::/0"]}}],"outbound":[],"version":3},"entities":[{"id":101,"label":"web-01","type":"linode"}]},
                   {"id":12,"label":"db-fw","status":"disabled","rules":{"inbound_policy":"DROP","outbound_policy":"ACCEPT","inbound":[],"outbound":[]},"entities":[]}""",
                    )
                path.matches(Regex("networking/firewalls/\\d+")) ->
                    """{"id":11,"label":"web-fw","status":"enabled","created":"2026-04-01T00:00:00","updated":"2026-09-01T00:00:00","rules":{"inbound_policy":"DROP","outbound_policy":"ACCEPT","inbound":[{"action":"ACCEPT","label":"ssh","protocol":"TCP","ports":"22","addresses":{"ipv4":["0.0.0.0/0"]}}],"outbound":[],"version":3},"entities":[]}"""
                path.endsWith("/devices") -> page("""{"id":1,"entity":{"id":101,"label":"web-01","type":"linode"}}""")
                path == "domains" ->
                    page(
                        """{"id":21,"domain":"example.com","type":"master","status":"active","soa_email":"hostmaster@example.com"}""",
                    )
                path == "nodebalancers" -> page("")
                path == "lke/clusters" -> page("""{"id":31,"label":"k8s-prod","region":"us-east","k8s_version":"1.33","status":"ready"}""")
                path == "regions" ->
                    page(
                        """{"id":"us-east","label":"Newark, NJ","country":"us","status":"ok","capabilities":["Linodes","Block Storage"]},{"id":"eu-central","label":"Frankfurt, DE","country":"de","status":"ok","capabilities":["Linodes","Block Storage"]}""",
                    )
                path == "linode/types" ->
                    page(
                        """{"id":"g6-nanode-1","label":"Nanode 1 GB","class":"nanode","vcpus":1,"memory":1024,"disk":25600,"price":{"monthly":5.0},"addons":{"backups":{"price":{"monthly":2.0}}}},
                   {"id":"g6-standard-2","label":"Linode 4 GB","class":"standard","vcpus":2,"memory":4096,"disk":81920,"price":{"monthly":24.0}},
                   {"id":"g6-standard-4","label":"Linode 8 GB","class":"standard","vcpus":4,"memory":8192,"disk":163840,"price":{"monthly":48.0}},
                   {"id":"g6-dedicated-4","label":"Dedicated 8 GB","class":"dedicated","vcpus":4,"memory":8192,"disk":163840,"price":{"monthly":72.0}}""",
                    )
                path == "images" ->
                    page(
                        """{"id":"linode/ubuntu24.04","label":"Ubuntu 24.04 LTS","vendor":"Ubuntu","is_public":true},{"id":"linode/debian12","label":"Debian 12","vendor":"Debian","is_public":true}""",
                    )
                else -> return MockResponse().setResponseCode(404).setBody("""{"errors":[{"reason":"Not found: $path"}]}""")
            }
        return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    }
}
