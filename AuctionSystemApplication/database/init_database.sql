-- Auction System Database
-- Phiên bản: 2.0

SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0;
SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0;
SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';

-- -----------------------------------------------------
-- Schema mydb
-- -----------------------------------------------------
CREATE SCHEMA IF NOT EXISTS `mydb` DEFAULT CHARACTER SET utf8;
USE `mydb`;

-- -----------------------------------------------------
-- Bảng users (gộp luôn role + rating vào đây)
-- role: 'bidder' | 'seller' | 'admin'
-- rating: chỉ có giá trị khi role = 'seller', còn lại NULL
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `mydb`.`users` (
                                              `id`         VARCHAR(36)                        NOT NULL,
    `name`       VARCHAR(45)                        NOT NULL,
    `username`   VARCHAR(45)                        NOT NULL UNIQUE,
    `password`   VARCHAR(255)                       NOT NULL,
    `balance`    DOUBLE                             NOT NULL DEFAULT 0,
    `is_active`  TINYINT(1)                         NOT NULL DEFAULT 1,
    `email`      VARCHAR(100)                       NOT NULL UNIQUE,
    `role`       ENUM('bidder', 'seller', 'admin')  NOT NULL DEFAULT 'bidder',
    `rating`     DOUBLE                             NULL DEFAULT NULL,
    PRIMARY KEY (`id`)
    ) ENGINE = InnoDB;

-- -----------------------------------------------------
-- Bảng items (seller_id trỏ thẳng về users.id)
-- status: 'pending' | 'active' | 'closed' | 'cancelled'
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `mydb`.`items` (
                                              `id`                    VARCHAR(36)                                        NOT NULL,
    `name`                  VARCHAR(255)                                       NOT NULL,
    `description`           TEXT                                               NULL,
    `starting_price`        DOUBLE                                             NOT NULL,
    `current_highest_price` DOUBLE                                             NULL DEFAULT NULL,
    `start_time`            DATETIME                                           NOT NULL,
    `end_time`              DATETIME                                           NOT NULL,
    `status`                ENUM('pending', 'active', 'closed', 'cancelled')  NOT NULL DEFAULT 'pending',
    `seller_id`             VARCHAR(36)                                        NOT NULL,
    PRIMARY KEY (`id`),
    INDEX `fk_items_seller_idx` (`seller_id` ASC),
    CONSTRAINT `fk_items_seller`
    FOREIGN KEY (`seller_id`)
    REFERENCES `mydb`.`users` (`id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION
    ) ENGINE = InnoDB;

-- -----------------------------------------------------
-- Bảng bids (bidder_id trỏ thẳng về users.id)
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `mydb`.`bids` (
                                             `id`         VARCHAR(36)  NOT NULL,
    `bid_amount` DOUBLE       NOT NULL,
    `bid_time`   DATETIME     NOT NULL,
    `bidder_id`  VARCHAR(36)  NOT NULL,
    `item_id`    VARCHAR(36)  NOT NULL,
    PRIMARY KEY (`id`),
    INDEX `fk_bids_bidder_idx` (`bidder_id` ASC),
    INDEX `fk_bids_item_idx`   (`item_id`   ASC),
    CONSTRAINT `fk_bids_bidder`
    FOREIGN KEY (`bidder_id`)
    REFERENCES `mydb`.`users` (`id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION,
    CONSTRAINT `fk_bids_item`
    FOREIGN KEY (`item_id`)
    REFERENCES `mydb`.`items` (`id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION
    ) ENGINE = InnoDB;

-- -----------------------------------------------------
-- Tạo tài khoản Admin mặc định
-- Password: "admin1234" đã hash bằng BCrypt
-- (Thay chuỗi hash bên dưới nếu muốn đổi password)
-- -----------------------------------------------------
INSERT INTO `mydb`.`users` (id, name, username, password, balance, is_active, email, role, rating)
VALUES (
           UUID(),
           'Administrator',
           'admin',
           '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
           0,
           1,
           'admin@auctionsystem.com',
           'admin',
           NULL
       );

SET SQL_MODE=@OLD_SQL_MODE;
SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS;
SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS;