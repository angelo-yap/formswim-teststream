package com.formswim.teststream.auth.config;

import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.FlashMapManager;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.servlet.support.SessionFlashMapManager;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

final class FlashMessageSupport {

    private FlashMessageSupport() {
    }

    static void addFlashMessage(HttpServletRequest request,
                                HttpServletResponse response,
                                String attributeName,
                                String attributeValue) {
        String targetPath = request == null ? null : request.getRequestURI();
        addFlashMessage(request, response, attributeName, attributeValue, targetPath);
    }

    static void addFlashMessage(HttpServletRequest request,
                                HttpServletResponse response,
                                String attributeName,
                                String attributeValue,
                                String targetPath) {
        FlashMap flashMap = RequestContextUtils.getOutputFlashMap(request);
        if (flashMap == null) {
            flashMap = new FlashMap();
            if (request != null) {
                request.setAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE, flashMap);
            }
        }

        if (targetPath != null && !targetPath.isBlank()) {
            flashMap.setTargetRequestPath(targetPath);
        }

        flashMap.put(attributeName, attributeValue);

        FlashMapManager flashMapManager = RequestContextUtils.getFlashMapManager(request);
        if (flashMapManager == null) {
            flashMapManager = new SessionFlashMapManager();
            if (request != null) {
                request.setAttribute(DispatcherServlet.FLASH_MAP_MANAGER_ATTRIBUTE, flashMapManager);
            }
        }

        flashMapManager.saveOutputFlashMap(flashMap, request, response);
    }
}
