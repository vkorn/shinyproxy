/**
 * ShinyProxy
 *
 * Copyright (C) 2016-2017 Open Analytics
 *
 * ===========================================================================
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License as published by
 * The Apache Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Apache License for more details.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/>
 */
package eu.openanalytics.controllers;

import eu.openanalytics.services.DockerService;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class AdminController extends BaseController {

	@Inject
	DockerService dockerService;

	private static final Logger LOGGER = LoggerFactory.getLogger(AdminController.class);
	@RequestMapping("/admin")
	String admin(ModelMap map, HttpServletRequest request) {
		prepareMap(map, request);
		map.put("proxies", dockerService.listProxies());
		return "admin";
	}

	@RequestMapping(value = "/p")
	public String index(HttpSession httpSession) {

		Integer hits = (Integer) httpSession.getAttribute("hits");

		LOGGER.info("index() called, hits was '{}', session id '{}'", hits, httpSession.getId());

		if (hits == null) {
			hits = 0;
		}

		httpSession.setAttribute("hits", ++hits);

		return "index";
	}
}
