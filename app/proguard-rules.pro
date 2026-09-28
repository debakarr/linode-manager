# Keep API models (Gson reflection)
-keep class com.linode.manager.data.remote.** { *; }
# Gson-persisted app data (device keys, SSH connection presets)
-keep class com.linode.manager.data.DeviceSshKey { *; }
-keep class com.linode.manager.data.ssh.SshProfile { *; }

# sshlib (ConnectBot) instantiates ciphers, MACs, KEX and signature
# implementations by class name (BlockCipherFactory, KexManager). Keep the
# library intact so release builds negotiate exactly what tests exercised.
-keep class com.trilead.ssh2.** { *; }
-dontwarn com.trilead.ssh2.**
# Optional post-quantum KEX backend, probed with Class.forName.
-keep class asia.hombre.kyber.** { *; }
-dontwarn asia.hombre.kyber.**
# Tink primitives used by sshlib for Ed25519/X25519.
-keep class com.google.crypto.tink.subtle.** { *; }
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
# termlib ships consumer rules for its JNI surface; nothing else needed.
