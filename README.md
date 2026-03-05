# simupay-log

Drop-in replacement for `android.util.Log` that forwards log entries to the
[SimuPay](https://sim.embeddedc.co.uk) dashboard in real time.

## Setup

### 1. Add JitPack to your project's `settings.gradle`
```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

### 2. Add the dependency to your app's `build.gradle`
```groovy
dependencies {
    implementation 'com.github.EmbeddedTechnologies:simupay-log:1.0.0'
}
```

### 3. Replace the import in your Java files
```java
// Remove:
import android.util.Log;

// Replace with:
import com.etl.simupaylog.Log;
```

All existing `Log.v/d/i/w/e` calls work unchanged.

### 4. Configure at startup (e.g. `Application.onCreate`)
```java
Log.setSimulatorUrl("https://sim.embeddedc.co.uk/api/log");
Log.setTerminalId("12345678");  // your 8-digit terminal ID
```

## Image logging
```java
// Send a bitmap as a log entry (shown in the dashboard with a View button)
Log.img("MyTag", "Card scan result", bitmap);
Log.img("MyTag", bitmap);  // no caption
```

## Requirements
- Android API 26+ (minSdk 26)
