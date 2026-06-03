package org.auctionsystem.server.handler;

import com.google.gson.JsonObject;
import org.auctionsystem.server.service.ItemService;

public class ItemHandler {
    private final ItemService itemService = new ItemService();

    public JsonObject handleAddItem(JsonObject request) {
        return itemService.addItem(request);
    }

    public JsonObject handleUpdateItem(JsonObject request) {
        return itemService.updateItem(request);
    }

    public JsonObject handleDeleteItem(JsonObject request) {
        return itemService.deleteItem(request);
    }

    public JsonObject handleCancelItem(JsonObject request) {
        return itemService.cancelItem(request);
    }

    public JsonObject handleGetItem(JsonObject request) {
        return itemService.getAItemById(request);
    }

    public JsonObject handleGetItemsBySeller(JsonObject request) {
        return itemService.getItemsBySeller(request);
    }

    public JsonObject handleGetItemsByOwner(JsonObject request) {
        return itemService.getItemsByOwner(request);
    }

    public JsonObject handleGetAllItems(JsonObject request) {
        return itemService.getAllItems(request);
    }

    public JsonObject handleGetVisibleItems(JsonObject request) {
        return itemService.getVisibleItems(request);
    }

    public JsonObject handleGetActiveItems(JsonObject request) {
        return itemService.getAllActiveItems(request);
    }

    public JsonObject handleUpdateItemStatus(JsonObject request) {
        return itemService.updateItemStatus(request);
    }

    public JsonObject handleRestartAuction(JsonObject request) {
        return itemService.restartItemAuction(request);
    }

    public JsonObject handleRestoreHiddenItem(JsonObject request) {
        return itemService.restoreHiddenItem(request);
    }

    public JsonObject handleSearchItems(JsonObject request) {
        return itemService.searchItems(request);
    }
}
