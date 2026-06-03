-- =====================================================
-- Auction System Database
-- Phiên bản: 2.5
-- Thay đổi so với 2.4: thêm bảng seller_ratings để lưu lịch sử đánh giá,
--   đảm bảo mỗi bidder chỉ đánh giá 1 seller 1 lần (có thể sửa điểm sau).
-- =====================================================

SET @OLD_UNIQUE_CHECKS = @@UNIQUE_CHECKS, UNIQUE_CHECKS = 0;
SET @OLD_FOREIGN_KEY_CHECKS = @@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS = 0;
SET @OLD_SQL_MODE = @@SQL_MODE, SQL_MODE =
        'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';

CREATE SCHEMA IF NOT EXISTS `mydb` DEFAULT CHARACTER SET utf8;
USE `mydb`;

-- -----------------------------------------------------
-- Bảng users
-- role: 'bidder' | 'seller' | 'admin'
-- rating: chỉ có giá trị khi role = 'seller'
-- rating_count: số lần được đánh giá — dùng để tính rating trung bình
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `mydb`.`users`
(
    `id`           VARCHAR(36)                        NOT NULL,
    `name`         VARCHAR(45)                        NOT NULL,
    `username`     VARCHAR(45)                        NOT NULL UNIQUE,
    `password`     VARCHAR(255)                       NOT NULL,
    `balance`      DOUBLE                             NOT NULL DEFAULT 0,
    `is_active`    TINYINT(1)                         NOT NULL DEFAULT 1,
    `email`        VARCHAR(100)                       NOT NULL UNIQUE,
    `role`         ENUM ('bidder', 'seller', 'admin') NOT NULL DEFAULT 'bidder',
    `phone`        VARCHAR(20)                        NULL     DEFAULT NULL,
    `rating`       DOUBLE                             NULL     DEFAULT NULL,
    `rating_count` INT                                NOT NULL DEFAULT 0,
    `avatar_url`   VARCHAR(255)                       NULL     DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE = InnoDB;

-- -----------------------------------------------------
-- Bảng items
-- status: 'pending' | 'active' | 'closed' | 'cancelled'
-- owner_id: ban đầu = seller_id, sau khi closed = buyer_id
-- is_active: 1 = bình thường, 0 = soft delete (chỉ áp dụng khi status = 'cancelled')
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
    `is_active`             TINYINT(1)                                        NOT NULL DEFAULT 1,
    `seller_id`             VARCHAR(36)                                       NOT NULL,
    `owner_id`              VARCHAR(36)                                       NOT NULL,
    `image_url`             VARCHAR(255)                                      NULL     DEFAULT NULL,
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
    INDEX `fk_bids_bidder_idx` (`bidder_id` ASC),
    INDEX `fk_bids_item_idx` (`item_id` ASC),
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
    INDEX `fk_history_item_idx` (`item_id` ASC),
    INDEX `fk_history_seller_idx` (`seller_id` ASC),
    INDEX `fk_history_buyer_idx` (`buyer_id` ASC),
    CONSTRAINT `fk_history_item`   FOREIGN KEY (`item_id`)   REFERENCES `mydb`.`items` (`id`),
    CONSTRAINT `fk_history_seller` FOREIGN KEY (`seller_id`) REFERENCES `mydb`.`users` (`id`),
    CONSTRAINT `fk_history_buyer`  FOREIGN KEY (`buyer_id`)  REFERENCES `mydb`.`users` (`id`)
) ENGINE = InnoDB;

-- -----------------------------------------------------
-- Bảng transactions
-- Ghi nhận mọi biến động số dư của tài khoản.
-- type: 'DEPOSIT' | 'WITHDRAW' | 'BID_DEDUCT' | 'BID_CREDIT'
-- related_item_id: NULL nếu không liên quan đến đấu giá
-- amount: luôn dương; chiều +/- suy từ type
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `mydb`.`transactions`
(
    `id`              VARCHAR(36)                                          NOT NULL,
    `user_id`         VARCHAR(36)                                          NOT NULL,
    `type`            ENUM ('DEPOSIT', 'WITHDRAW', 'BID_DEDUCT', 'BID_CREDIT') NOT NULL,
    `amount`          DOUBLE                                               NOT NULL,
    `balance_before`  DOUBLE                                               NOT NULL,
    `balance_after`   DOUBLE                                               NOT NULL,
    `related_item_id` VARCHAR(36)                                          NULL     DEFAULT NULL,
    `note`            VARCHAR(255)                                         NULL     DEFAULT NULL,
    `created_at`      DATETIME                                             NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `fk_transactions_user_idx`    (`user_id`         ASC),
    INDEX `fk_transactions_item_idx`    (`related_item_id` ASC),
    CONSTRAINT `fk_transactions_user`   FOREIGN KEY (`user_id`)         REFERENCES `mydb`.`users` (`id`),
    CONSTRAINT `fk_transactions_item`   FOREIGN KEY (`related_item_id`) REFERENCES `mydb`.`items` (`id`)
) ENGINE = InnoDB;

-- -----------------------------------------------------
-- Bảng images
-- Lưu metadata của mọi file ảnh trên disk.
-- owner_type: 'avatar' → owner_id là user_id
--             'item'   → owner_id là item_id
-- file_path: đường dẫn tương đối, ví dụ "avatars/uuid.jpg", "items/uuid.png"
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `mydb`.`images`
(
    `id`         VARCHAR(36)          NOT NULL,
    `file_path`  VARCHAR(255)         NOT NULL UNIQUE,
    `owner_type` ENUM('avatar','item') NOT NULL,
    `owner_id`   VARCHAR(36)          NOT NULL,
    `created_at` DATETIME             NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_images_owner` (`owner_type` ASC, `owner_id` ASC)
) ENGINE = InnoDB;

-- -----------------------------------------------------
-- Bảng seller_ratings
-- Lưu lịch sử đánh giá: mỗi (bidder_id, seller_id) chỉ có tối đa 1 dòng.
-- Bidder có thể cập nhật điểm (rating_score) bất cứ lúc nào,
-- nhưng không thể tạo thêm 1 đánh giá mới cho cùng 1 seller.
-- rated_at: thời điểm đánh giá lần đầu
-- updated_at: thời điểm cập nhật điểm gần nhất
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS `mydb`.`seller_ratings`
(
    `id`           VARCHAR(36) NOT NULL,
    `bidder_id`    VARCHAR(36) NOT NULL,
    `seller_id`    VARCHAR(36) NOT NULL,
    `rating_score` TINYINT     NOT NULL COMMENT '1–5',
    `rated_at`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_bidder_seller` (`bidder_id`, `seller_id`),
    INDEX `fk_sr_bidder_idx` (`bidder_id` ASC),
    INDEX `fk_sr_seller_idx` (`seller_id` ASC),
    CONSTRAINT `fk_sr_bidder` FOREIGN KEY (`bidder_id`) REFERENCES `mydb`.`users` (`id`),
    CONSTRAINT `fk_sr_seller` FOREIGN KEY (`seller_id`) REFERENCES `mydb`.`users` (`id`)
) ENGINE = InnoDB;

SET SQL_MODE = @OLD_SQL_MODE;
SET FOREIGN_KEY_CHECKS = @OLD_FOREIGN_KEY_CHECKS;
SET UNIQUE_CHECKS = @OLD_UNIQUE_CHECKS;