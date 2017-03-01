package jbot.slack.util;

import me.ramswaroop.jbot.core.slack.models.Event;

public class UtilSlack {

	public static final int ONLY_ONE_PADAWAN = 1;
    
	public static final String DELIMITER_AVOID_NOTIFICATION = "I";

	public static final String YODA_EMOTICON = ":yoda:";
    
	public static final String DARTH_EMOTICON = ":darth:";
    
	public static final String C3PO_EMOTICON = ":c3po:";
    
	public static final String STORMTROOPER_EMOTICON = ":storm:";
    
	public static final String R2D2_EMOTICON = ":r2d2:";
	
	public static final String NOT_ALLOWED_ORDER = " Respeite seus mestres! Você não é capaz de tal ordem! ";
	
	public static final String EMPTY_LIST = " A fila está vazia! ";
	
	public static final String EMPTY_LIST_YODA_SPEECH = " A fila vazia está! ";
	
	public static final String WRONG_TURN_YODA_SPEECH = " sua vez não era! A fila você furou? ";
	
	public static String NOTIFICATION_DEFAULT = "[%s]";
	
	public static String retrieveNotification(Event event) {
		return String.format(NOTIFICATION_DEFAULT, event.getUser().getName());
	}
	
	public static String retrievePadawanNameNotification(Event event) {
		return "Padawan @" + event.getUser().getName();
	}
	
}
