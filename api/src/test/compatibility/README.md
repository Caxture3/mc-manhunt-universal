# API 1.0 binary compatibility fixture

`../resources/compatibility/LegacyApiConsumer.class` was compiled before the 1.1 interface changes, against the shipped API 1.0.0 binary, using `javac --release 21`.

Original API JAR SHA-256: `aab08837796acb0d0cd64d8ca07bad96d0df2eaa83e3539f1a7f9a655da75333`
Fixture SHA-256: `e813702cdb95b03da10014862b65e6982fcd3f17e0c7b23f83b3ab4c92108b75`

The adjacent source is for reproducibility and is deliberately outside Gradle test source roots. Do not recompile this fixture against 1.1: `Api11CompatibilityTest` must load unchanged 1.0-consumer bytecode. To recreate it, obtain that original 1.0 JAR and run from the repository root:

```powershell
javac --release 21 -cp <original-api-1.0.0.jar> -d api/src/test/resources api/src/test/compatibility/LegacyApiConsumer.java
```

The test verifies old constructors/method calls still link, and a legacy implementation inherits the new optional default entrypoints without an `AbstractMethodError`.

## API 1.1 tracking fixture

`LegacyTrackingConsumer.class` was compiled with `javac --release 21` against the shipped 1.1.0 JAR (SHA-256 `c481e08416dd9d083cdeb620d4c08d7c2886dd13054cf96ecd40008ca7effe27`). Its SHA-256 is `9e65ff2aff967b89250a4eb7de51e23264e1b57d87bc919651dc7ed3d90f928d`. `Api12CompatibilityTest` loads this original bytecode and checks the new default control entry. Do not recompile against the newer SDK.
