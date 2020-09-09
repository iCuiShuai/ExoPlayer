//
//  VAST_DOC_ELEMENTS.java
//
//  Copyright (c) 2014 Nexage. All rights reserved.
//


package com.mxplay.offlineads.exo.vast.model;

public enum VAST_DOC_ELEMENTS {

	vastVersion ("2.0"),
	vasts ("VASTS"),
	vast("VAST"),
	vastAdTagURI ("VASTAdTagURI"),
	vastAdTagData ("VASTAdData"),
	vastVersionAttribute ("version");

	private String value;

	private VAST_DOC_ELEMENTS(String value) {
		this.value = value;

	}

	public String getValue() {
		return value;
	}

}
