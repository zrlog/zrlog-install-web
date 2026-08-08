# 这个文件仅用于程序初始化数据库表结构使用, 程序初始化使用提供的安装向导进行初始化, 及不要手动导入这个文件到数据库
# Date: 2014-07-22 13:23:19
# Generator: MySQL-Front 5.3  (Build 4.43)

/*!40101 SET NAMES utf8 */;

DROP TABLE IF EXISTS `comment`, `link`, `log`, `log_version`, `lognav`, `plugin`, `tag`, `type`,
    `user_passkey_challenge`, `user_passkey`, `user`, `website`;

#
# Structure for table "link"
#

DROP TABLE IF EXISTS `link`;
CREATE TABLE `link`
(
    `linkId`   int(11) NOT NULL AUTO_INCREMENT,
    `alt`      varchar(255) DEFAULT NULL,
    `linkName` varchar(255) DEFAULT NULL,
    `sort`     int(11) DEFAULT NULL,
    `status`   bit(1)       DEFAULT NULL,
    `url`      varchar(255) DEFAULT NULL,
    `icon`     longtext     DEFAULT NULL,
    PRIMARY KEY (`linkId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

#
# Structure for table "lognav"
#

DROP TABLE IF EXISTS `lognav`;
CREATE TABLE `lognav`
(
    `navId`   int(11) NOT NULL AUTO_INCREMENT,
    `navName` varchar(32)  DEFAULT NULL,
    `sort`    int(11) DEFAULT NULL,
    `url`     varchar(255) DEFAULT NULL,
    `icon`    longtext     DEFAULT NULL,
    PRIMARY KEY (`navId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

#
# Structure for table "plugin"
#

DROP TABLE IF EXISTS `plugin`;
CREATE TABLE `plugin`
(
    `pluginId`   int(11) NOT NULL AUTO_INCREMENT,
    `content`    text,
    `isSystem`   bit(1)       DEFAULT NULL,
    `pTitle`     varchar(255) DEFAULT NULL,
    `sort`       int(11) DEFAULT NULL,
    `pluginName` varchar(255) DEFAULT NULL,
    `level`      int(11) DEFAULT NULL,
    PRIMARY KEY (`pluginId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

#
# Structure for table "tag"
#

DROP TABLE IF EXISTS `tag`;
CREATE TABLE `tag`
(
    `tagId` int(11) NOT NULL AUTO_INCREMENT,
    `count` int(11) NOT NULL DEFAULT '0',
    `text`  varchar(64) DEFAULT NULL,
    PRIMARY KEY (`tagId`),
    UNIQUE KEY `text` (`text`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

#
# Structure for table "type"
#

DROP TABLE IF EXISTS `log`;
DROP TABLE IF EXISTS `type`;
CREATE TABLE `type`
(
    `typeId`   int(11) NOT NULL AUTO_INCREMENT,
    `alias`    varchar(32)   DEFAULT NULL,
    `remark`   varchar(2000) DEFAULT NULL,
    `typeName` varchar(128)  DEFAULT NULL,
    `pid`      int(11) DEFAULT NULL,
    `arrange_plugin`  varchar(64)  DEFAULT null COMMENT '文章分类统筹重排插件名称',
    PRIMARY KEY (`typeId`),
    UNIQUE KEY `alias` (`alias`),
    KEY        `pid` (`pid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


#
# Structure for table "user"
#

DROP TABLE IF EXISTS `user`;
CREATE TABLE `user`
(
    `userId`            int(11) NOT NULL AUTO_INCREMENT,
    `email`             varchar(64)   DEFAULT NULL,
    `password`          varchar(128)  DEFAULT NULL,
    `userName`          varchar(16)   DEFAULT NULL,
    `header`            varchar(255)  DEFAULT NULL,
    `secretKey`         varchar(1024) DEFAULT NULL COMMENT '密钥',
    `mfaEnabled`        bit(1)        DEFAULT b'0' COMMENT '是否启用 MFA',
    `mfaSecret`         varchar(128)  DEFAULT NULL COMMENT 'MFA 密钥',
    `passkeyUserHandle` varchar(64)   DEFAULT NULL,
    PRIMARY KEY (`userId`),
    UNIQUE KEY `userName` (`userName`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE UNIQUE INDEX `user_passkey_handle`
    ON `user` (`passkeyUserHandle`);

#
# Structure for table "user_passkey"
#

DROP TABLE IF EXISTS `user_passkey`;
CREATE TABLE `user_passkey`
(
    `id`               int(11) NOT NULL AUTO_INCREMENT,
    `userId`           int(11)      NOT NULL,
    `credentialIdHash` varchar(64)  NOT NULL,
    `credentialId`     longtext     NOT NULL,
    `publicKeyCose`    longtext     NOT NULL,
    `signatureCount`   bigint       NOT NULL DEFAULT 0,
    `transports`       varchar(255) DEFAULT NULL,
    `name`             varchar(128) DEFAULT NULL,
    `aaguid`           varchar(64)  DEFAULT NULL,
    `backupEligible`   bit(1)       NOT NULL DEFAULT b'0',
    `backupState`      bit(1)       NOT NULL DEFAULT b'0',
    `origin`           varchar(512) NOT NULL,
    `rpId`             varchar(255) NOT NULL,
    `createdAt`        bigint       NOT NULL,
    `lastUsedAt`       bigint       DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE UNIQUE INDEX `user_passkey_credential_hash`
    ON `user_passkey` (`credentialIdHash`);
CREATE INDEX `user_passkey_user`
    ON `user_passkey` (`userId`);

#
# Structure for table "user_passkey_challenge"
#

DROP TABLE IF EXISTS `user_passkey_challenge`;
CREATE TABLE `user_passkey_challenge`
(
    `id`          int(11) NOT NULL AUTO_INCREMENT,
    `requestId`   varchar(64) NOT NULL,
    `ceremony`    varchar(32) NOT NULL,
    `userId`      int(11) DEFAULT NULL,
    `requestJson` longtext    NOT NULL,
    `createdAt`   bigint      NOT NULL,
    `expiresAt`   bigint      NOT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE UNIQUE INDEX `user_passkey_challenge_request`
    ON `user_passkey_challenge` (`requestId`);
CREATE INDEX `user_passkey_challenge_expiry`
    ON `user_passkey_challenge` (`expiresAt`);
CREATE INDEX `user_passkey_challenge_user_ceremony`
    ON `user_passkey_challenge` (`userId`, `ceremony`);

#
# Structure for table "log"
#

DROP TABLE IF EXISTS `log`;
CREATE TABLE `log`
(
    `logId`            int(11) NOT NULL AUTO_INCREMENT,
    `alias`            varchar(64)  DEFAULT NULL,
    `canComment`       bit(1)       DEFAULT b'1',
    `click`            int(11) DEFAULT '0',
    `version`          int(11) DEFAULT '0',
    `content`          longtext,
    `plain_content`    longtext,
    `markdown`         longtext,
    `digest`           text,
    `keywords`         varchar(255) DEFAULT NULL,
    `thumbnail`        varchar(255) DEFAULT NULL,
    `recommended`      bit(1)       DEFAULT b'0',
    `sticky`           INTEGER      NOT NULL DEFAULT 0,
    `releaseTime`      datetime     DEFAULT NULL,
    `last_update_date` datetime     DEFAULT NULL,
    `title`            varchar(255) DEFAULT NULL,
    `typeId`           int(11) DEFAULT NULL,
    `userId`           int(11) DEFAULT NULL,
    `hot`              bit(1)       DEFAULT NULL,
    `rubbish`          bit(1)       DEFAULT NULL,
    `privacy`          bit(1)       DEFAULT NULL,
    `editor_type`      varchar(256) DEFAULT NULL,
    `arrange_plugin`   varchar(64)  DEFAULT null COMMENT '文章统筹重排插件名称',
    `extensions`       longtext,
    PRIMARY KEY (`logId`),
    KEY                `typeId` (`typeId`),
    KEY                `userId` (`userId`),
    UNIQUE KEY `alias` (`alias`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_unicode_ci;

#
# Structure for table "log_extension_index"
#

DROP TABLE IF EXISTS `log_extension_index`;
CREATE TABLE `log_extension_index`
(
    `id`              int(11) NOT NULL AUTO_INCREMENT,
    `log_id`          int(11)      NOT NULL,
    `namespace`       varchar(64)  NOT NULL,
    `extension_path`  varchar(191) NOT NULL,
    `extension_value` varchar(512) DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE INDEX `log_extension_article`
    ON `log_extension_index` (`log_id`, `namespace`);
CREATE INDEX `log_extension_filter`
    ON `log_extension_index` (`namespace`, `extension_path`, `extension_value`);


#
# Structure for table "comment"
#

DROP TABLE IF EXISTS `comment`;
CREATE TABLE `comment`
(
    `commentId`   int(11) NOT NULL AUTO_INCREMENT,
    `commTime`    datetime      DEFAULT NULL,
    `hide`        bit(1)        DEFAULT NULL,
    `have_read`   bit(1)        DEFAULT false COMMENT '评论是否已读',
    `td`          datetime      DEFAULT NULL,
    `userComment` varchar(2048) DEFAULT NULL,
    `userHome`    varchar(64)   DEFAULT NULL,
    `userIp`      varchar(64)   DEFAULT NULL,
    `userMail`    varchar(64)   DEFAULT NULL,
    `userName`    varchar(64)   DEFAULT NULL,
    `logId`       int(11) DEFAULT NULL,
    `postId`      varchar(128)  DEFAULT NULL COMMENT '多说评论使用',
    `header`      varchar(1024) DEFAULT NULL COMMENT '评论者头像',
    `user_agent`  varchar(1024) DEFAULT NULL COMMENT '浏览器信息',
    `reply_id`    int(11) DEFAULT NULL COMMENT '回复评论的ID',
    PRIMARY KEY (`commentId`),
    UNIQUE KEY `postId` (`postId`),
    KEY           `logId` (`logId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


#
# Structure for table "log_version"
#

DROP TABLE IF EXISTS `log_version`;
CREATE TABLE `log_version`
(
    `id`              int(11) NOT NULL AUTO_INCREMENT,
    `log_id`          int(11) NOT NULL,
    `article_version` int(11) NOT NULL,
    `from_version`    int(11) NOT NULL,
    `patch_json`      longtext,
    `title`           varchar(255) DEFAULT NULL,
    `user_id`         int(11) DEFAULT NULL,
    `created_at`      datetime     DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `log_id_article_version` (`log_id`, `article_version`),
    KEY               `log_id` (`log_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_unicode_ci;


#
# Structure for table "website"
#

DROP TABLE IF EXISTS `website`;
CREATE TABLE `website`
(
    `siteId` int(11) NOT NULL AUTO_INCREMENT,
    `name`   varchar(255)  DEFAULT NULL,
    `value`  longtext      DEFAULT NULL,
    `remark` varchar(2000) DEFAULT NULL,
    PRIMARY KEY (`siteId`),
    UNIQUE KEY `name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
