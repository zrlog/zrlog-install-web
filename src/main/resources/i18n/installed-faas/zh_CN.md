#### 还差一步

请在函数平台的环境变量或 Secret 配置中添加以下两项，然后发布或重启当前函数版本。所有并发实例和版本别名应使用相同配置。

##### `DB_PROPERTIES`

将下面的完整内容原样设置为 `DB_PROPERTIES`：

```properties
${dbProperties}
```

##### `TZ`

将 `TZ` 设置为：

```text
Asia/Shanghai
```

配置完成后，确认公开路由指向新版本，再返回本页检查配置。
