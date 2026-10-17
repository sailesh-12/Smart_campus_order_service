package com.smartcampus.menu.dto;

/**
 * Estimated time range (in minutes) for an order or menu item.
 */
public record EtaRange(
        int lowMinutes,
        int highMinutes
) {
}
