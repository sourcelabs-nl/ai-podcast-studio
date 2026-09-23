---
name: jackson-migration
description: Use when working with Jackson JSON serialization in Spring Boot 4+ projects that use Jackson 3.x, or when migrating code from Jackson 2.x to 3.x. Covers package changes, class renames, annotation differences, and common pitfalls.
user-invocable: false
---

# Jackson 2.x to 3.x Migration

## Overview

Jackson 3.x (released October 2025) introduced breaking changes in package names, class names, and configuration patterns. Spring Boot 4.x uses Jackson 3.x by default. This project is on Spring Boot 4.x and already uses the `tools.jackson.*` (Jackson 3.x) packages; this skill is the quick reference for keeping new code correct and for spotting stray Jackson 2.x usage.

## When This Applies

- Spring Boot 4.x projects (uses Jackson 3.x)
- Code importing from `com.fasterxml.jackson.databind` or `com.fasterxml.jackson.core`
- Missing bean errors for `ObjectMapper`, `JsonFactory`, or other Jackson core types
- Compilation errors after upgrading Spring Boot from 3.x to 4.x

## The One Exception: Annotations Stay at 2.x

Jackson 3.x deliberately keeps `jackson-annotations` at the **old** package (`com.fasterxml.jackson.annotation`) so annotations can be shared between Jackson 2.x and 3.x code.

```kotlin
// These STILL work in Jackson 3.x / Spring Boot 4.x:
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
```

However, annotations from `jackson-databind` (under `tools.jackson.databind.annotation`) **did** move:

```kotlin
// These MOVED to the new package:
import tools.jackson.databind.annotation.JsonNaming    // was com.fasterxml.jackson.databind.annotation
import tools.jackson.databind.annotation.JsonSerialize  // was com.fasterxml.jackson.databind.annotation
import tools.jackson.databind.annotation.JsonDeserialize // was com.fasterxml.jackson.databind.annotation
```

## Package Changes

### Maven Group ID

| Jackson 2.x | Jackson 3.x |
|---|---|
| `com.fasterxml.jackson.core` | `tools.jackson.core` |
| `com.fasterxml.jackson.module` | `tools.jackson.module` |
| `com.fasterxml.jackson.dataformat` | `tools.jackson.dataformat` |
| `com.fasterxml.jackson.datatype` | `tools.jackson.datatype` |
| `com.fasterxml.jackson.core:jackson-annotations` | **Unchanged** (`com.fasterxml.jackson.core:jackson-annotations:2.20`) |

### Java/Kotlin Package

| Jackson 2.x | Jackson 3.x |
|---|---|
| `com.fasterxml.jackson.databind.ObjectMapper` | `tools.jackson.databind.ObjectMapper` |
| `com.fasterxml.jackson.databind.JsonNode` | `tools.jackson.databind.JsonNode` |
| `com.fasterxml.jackson.core.JsonParser` | `tools.jackson.core.JsonParser` |
| `com.fasterxml.jackson.core.JsonGenerator` | `tools.jackson.core.JsonGenerator` |
| `com.fasterxml.jackson.core.JsonFactory` | `tools.jackson.core.TokenStreamFactory` |
| `com.fasterxml.jackson.module.kotlin.*` | `tools.jackson.module.kotlin.*` |
| `com.fasterxml.jackson.annotation.*` | **Unchanged** |

## Class Renames

### Core Types

| Jackson 2.x | Jackson 3.x |
|---|---|
| `JsonDeserializer` | `ValueDeserializer` |
| `JsonSerializer` | `ValueSerializer` |
| `SerializerProvider` | `SerializationContext` |
| `JsonFactory` | `TokenStreamFactory` |
| `Module` | `JacksonModule` |
| `JsonSerializable` | `JacksonSerializable` |
| `TextNode` | `StringNode` |

### Exception Types

| Jackson 2.x | Jackson 3.x |
|---|---|
| `JsonProcessingException` | `JacksonException` |
| `JsonMappingException` | `DatabindException` |
| `JsonParseException` | `StreamReadException` |
| `JsonGenerationException` | `StreamWriteException` |

## Method Renames

### JsonParser

| Jackson 2.x | Jackson 3.x |
|---|---|
| `getText()` | `getString()` |
| `getCodec()` | `objectReadContext()` |
| `getCurrentLocation()` | `currentLocation()` |
| `getCurrentValue()` | `currentValue()` |
| `setCurrentValue()` | `assignCurrentValue()` |

### JsonGenerator

| Jackson 2.x | Jackson 3.x |
|---|---|
| `getCodec()` | `objectWriteContext()` |
| `getCurrentValue()` | `currentValue()` |
| `setCurrentValue()` | `assignCurrentValue()` |
| `writeObject()` | `writePOJO()` |

### Fields

| Jackson 2.x | Jackson 3.x |
|---|---|
| `JsonToken.FIELD_NAME` | `JsonToken.PROPERTY_NAME` |

## ObjectMapper Is Now Immutable

In Jackson 3.x, `ObjectMapper` configuration requires the builder pattern:

```kotlin
// Jackson 2.x - mutable configuration
val mapper = ObjectMapper()
mapper.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
mapper.registerModule(KotlinModule.Builder().build())

// Jackson 3.x - immutable, use builder
val mapper = JsonMapper.builder()
    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
    .addModule(KotlinModule.Builder().build())
    .build()
```

## Handling snake_case JSON in Data Classes

### Option 1: Per-field with @JsonProperty (annotations jar, still works)

```kotlin
import com.fasterxml.jackson.annotation.JsonProperty

data class ApiResponse(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("refresh_token") val refreshToken: String? = null
)
```

### Option 2: Per-class with @JsonNaming (Jackson 3.x databind annotation)

```kotlin
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class ApiResponse(
    val accessToken: String,
    val refreshToken: String? = null
)
```

Option 2 is preferred for classes where all fields follow snake_case convention — less repetition and uses Jackson 3.x native API.

## Embedded Modules (No Separate Dependencies Needed)

Jackson 3.x embeds these modules into `jackson-databind` — remove their separate dependencies:

- Parameter names module
- `java.util.Optional` support
- `java.time` types support (`jackson-datatype-jsr310`)

## Spring Boot 4.x Specifics

### Bean Type

Spring Boot 4.x auto-configures `tools.jackson.databind.ObjectMapper`, not the 2.x variant. If you inject `ObjectMapper`, make sure you import from `tools.jackson.databind`.

```kotlin
// Wrong - will fail with "No qualifying bean" error
import com.fasterxml.jackson.databind.ObjectMapper

// Correct
import tools.jackson.databind.ObjectMapper
```

### RestTemplate and WebClient

Spring Boot 4.x configures `RestTemplate` and `WebClient` with Jackson 3.x message converters. Data classes used with these clients must be compatible with Jackson 3.x (annotations from `com.fasterxml.jackson.annotation` still work).

### Kotlin Module

```xml
<!-- Spring Boot 4.x managed dependency -->
<dependency>
    <groupId>tools.jackson.module</groupId>
    <artifactId>jackson-module-kotlin</artifactId>
</dependency>
```

```kotlin
// Kotlin usage
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

val mapper = jacksonObjectMapper()
val result: MyData = mapper.readValue(jsonString)
```

## Common Migration Pitfalls

1. **Mixed Jackson 2.x and 3.x on classpath**: Spring Boot 4.x can have both (e.g., via transitive dependencies). The auto-configured beans are 3.x. Check with `./mvnw dependency:tree '-Dincludes=*:jackson*'`.

2. **ObjectMapper injection fails**: You're importing from `com.fasterxml.jackson.databind` instead of `tools.jackson.databind`.

3. **Custom serializers/deserializers break**: Extend `ValueSerializer`/`ValueDeserializer` instead of `JsonSerializer`/`JsonDeserializer`.

4. **Format-specific mapper required**: `new ObjectMapper(new YAMLFactory())` no longer works. Use `new YAMLMapper()` instead.

5. **Feature enums moved**: `JsonParser.Feature` and `JsonGenerator.Feature` split into `StreamReadFeature`/`JsonReadFeature` and `StreamWriteFeature`/`JsonWriteFeature`.

## Quick Decision Guide

| Situation | Action |
|---|---|
| Using `@JsonProperty`, `@JsonIgnore`, etc. | Keep `com.fasterxml.jackson.annotation` import |
| Using `@JsonNaming`, `@JsonSerialize`, `@JsonDeserialize` | Change to `tools.jackson.databind.annotation` |
| Injecting `ObjectMapper` | Change to `tools.jackson.databind.ObjectMapper` |
| Custom `JsonSerializer`/`JsonDeserializer` | Rename to `ValueSerializer`/`ValueDeserializer` + change package |
| Catching `JsonProcessingException` | Change to `JacksonException` |
| Using `JsonFactory` | Change to `TokenStreamFactory` or format-specific factory |
| Configuring `ObjectMapper` mutably | Use `JsonMapper.builder()...build()` |
