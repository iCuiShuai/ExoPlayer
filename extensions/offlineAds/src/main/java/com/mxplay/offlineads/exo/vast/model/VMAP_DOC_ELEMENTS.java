//
//  VAST_DOC_ELEMENTS.java
//
//  Copyright (c) 2014 Nexage. All rights reserved.
//


package com.mxplay.offlineads.exo.vast.model;

public enum VMAP_DOC_ELEMENTS {

	vastVersion ("1.0"),
	adbreak ("vmap:AdBreak"),
	vastAdSource("vmap:AdSource"),
	vastAdData("vmap:VASTAdData");

	private String value;

	private VMAP_DOC_ELEMENTS(String value) {
		this.value = value;

	}

	public String getValue() {
		return value;
	}

}
