# simupay-log

Drop-in replacement for `android.util.Log` that forwards log entries to the
[SimuPay](https://sim.embeddedc.co.uk) dashboard in real time.

## Setup

### 1. Add JitPack to your project's `settings.gradle`
```groovy
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url 'https://jitpack.io' }
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == 'com.etl.simupay-log') {
                useModule("com.github.EmbeddedTechnologies.simupay-log:plugin:${requested.version}")
            }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

### 2. Add the plugin and dependency to your app's `build.gradle`
```groovy
plugins {
    id 'com.android.application'
    id 'com.etl.simupay-log' version 'v1.2.0'
}

dependencies {
    implementation 'com.github.EmbeddedTechnologies.simupay-log:lib:v1.2.0'
}
```

**That's it.** All existing `Log.v()`, `Log.d()`, `Log.i()`, `Log.w()`, `Log.e()` calls
are automatically redirected to the SimuPay logging library at build time — no import
changes or source modifications required.

### 3. Configure at startup (e.g. `Application.onCreate`)
```java
import com.etl.simupaylog.Log;

Log.setSimulatorUrl("https://sim.embeddedc.co.uk/api/log");
Log.setTerminalId("12345678");  // your 8-digit terminal ID
```

## How it works

The Gradle plugin uses Android's bytecode instrumentation API to rewrite all
`android.util.Log` method calls to `com.etl.simupaylog.Log` during compilation.
Your source code stays untouched — the redirect happens transparently in the
build pipeline.

The SimuPay `Log` class delegates to the real `android.util.Log` (so Logcat
still works) and also posts each entry to the SimuPay dashboard over HTTP.

## Image logging
```java
// Send a bitmap as a log entry (shown in the dashboard with a View button)
Log.img("MyTag", "Card scan result", bitmap);
Log.img("MyTag", bitmap);  // no caption
```

## Screenshot logging
```java
// Capture and send a screenshot of the current activity
Log.screen("MyTag", "Payment screen", activity);
Log.screen("MyTag", activity);  // no caption

// Capture a specific view
Log.screen("MyTag", "Card input", cardView);
```

## Manual import (alternative to plugin)

If you prefer not to use the Gradle plugin, you can manually replace imports:

```java
// Remove:
import android.util.Log;

// Replace with:
import com.etl.simupaylog.Log;
```

Add only the library dependency (no plugin needed):
```groovy
dependencies {
    implementation 'com.github.EmbeddedTechnologies.simupay-log:lib:v1.2.0'
}
```

## Requirements
- Android API 26+ (minSdk 26)
- Android Gradle Plugin 7.0+ (for the bytecode rewriting plugin)
