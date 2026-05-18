-- Auction System Database
-- Phiên bản: 2.1 (Sạch - Không kèm dữ liệu)

SET @OLD_UNIQUE_CHECKS = @@UNIQUE_CHECKS, UNIQUE_CHECKS = 0;
SET @OLD_FOREIGN_KEY_CHECKS = @@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS = 0;
SET @OLD_SQL_MODE = @@SQL_MODE, SQL_MODE =
        'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';

CREATE SCHEMA IF NOT EXISTS `mydb` DEFAULT CHARACTER SET utf8;
USE `mydb`;

-- -----------------------------------------------------
-- Bảng users
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `mydb`.`users`
(
    `id`        VARCHAR(36)                        NOT NULL,
    `name`      VARCHAR(45)                        NOT NULL,
    `username`  VARCHAR(45)                        NOT NULL UNIQUE,
    `password`  VARCHAR(255)                       NOT NULL,
    `balance`   DOUBLE                             NOT NULL DEFAULT 0,
    `is_active` TINYINT(1)                         NOT NULL DEFAULT 1,
    `email`     VARCHAR(100)                       NOT NULL UNIQUE,
    `role`      ENUM ('bidder', 'seller', 'admin') NOT NULL DEFAULT 'bidder',
    `phone`     VARCHAR(20)                        NULL     DEFAULT NULL,
    `rating`    DOUBLE                             NULL     DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE = InnoDB;

-- -----------------------------------------------------
-- Bảng items
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `mydb`.`items`
(
    `id`                    VARCHAR(36)                                       NOT NULL,
    `name`                  VARCHAR(255)                                      NOT NULL,
    `description`           TEXT                                              NULL,
    `starting_price`        DOUBLE                                            NOT NULL,
    `current_highest_price` DOUBLE                                            NULL     DEFAULT NULL,
    `start_time`            DATETIME                                          NOT NULL,
    `end_time`              DATETIME                                          NOT NULL,
    `status`                ENUM ('pending', 'active', 'closed', 'cancelled') NOT NULL DEFAULT 'pending',
    `seller_id`             VARCHAR(36)                                       NOT NULL,
    `owner_id`              VARCHAR(36)                                       NOT NULL,
    PRIMARY KEY (`id`),
    INDEX `fk_items_seller_idx` (`seller_id` ASC),
    INDEX `fk_items_owner_idx` (`owner_id` ASC),
    CONSTRAINT `fk_items_seller` FOREIGN KEY (`seller_id`) REFERENCES `mydb`.`users` (`id`),
    CONSTRAINT `fk_items_owner` FOREIGN KEY (`owner_id`) REFERENCES `mydb`.`users` (`id`)
) ENGINE = InnoDB;

-- -----------------------------------------------------
-- Bảng bids
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `mydb`.`bids`
(
    `id`         VARCHAR(36) NOT NULL,
    `bid_amount` DOUBLE      NOT NULL,
    `bid_time`   DATETIME    NOT NULL,
    `bidder_id`  VARCHAR(36) NOT NULL,
    `item_id`    VARCHAR(36) NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_bids_bidder` FOREIGN KEY (`bidder_id`) REFERENCES `mydb`.`users` (`id`),
    CONSTRAINT `fk_bids_item` FOREIGN KEY (`item_id`) REFERENCES `mydb`.`items` (`id`)
) ENGINE = InnoDB;

-- -----------------------------------------------------
-- Bảng item_ownership_history
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `mydb`.`item_ownership_history`
(
    `id`         VARCHAR(36) NOT NULL,
    `item_id`    VARCHAR(36) NOT NULL,
    `seller_id`  VARCHAR(36) NOT NULL,
    `buyer_id`   VARCHAR(36) NOT NULL,
    `sold_price` DOUBLE      NOT NULL,
    `sold_time`  DATETIME    NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_history_item` FOREIGN KEY (`item_id`) REFERENCES `mydb`.`items` (`id`),
    CONSTRAINT `fk_history_seller` FOREIGN KEY (`seller_id`) REFERENCES `mydb`.`users` (`id`),
    CONSTRAINT `fk_history_buyer` FOREIGN KEY (`buyer_id`) REFERENCES `mydb`.`users` (`id`)
) ENGINE = InnoDB;

SET SQL_MODE = @OLD_SQL_MODE;
SET FOREIGN_KEY_CHECKS = @OLD_FOREIGN_KEY_CHECKS;
SET UNIQUE_CHECKS = @OLD_UNIQUE_CHECKS;