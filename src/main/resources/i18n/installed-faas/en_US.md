#### One more step

Add the following two values to the function platform's environment variables or Secret configuration, then publish or restart the current function version. All concurrent instances and version aliases must use the same configuration.

##### `DB_PROPERTIES`

Set `DB_PROPERTIES` to the complete value below without modification:

```properties
${dbProperties}
```

##### `TZ`

Set `TZ` to:

```text
Asia/Shanghai
```

After saving the configuration, confirm that the public route points to the new version, then return to this page to check the configuration.
