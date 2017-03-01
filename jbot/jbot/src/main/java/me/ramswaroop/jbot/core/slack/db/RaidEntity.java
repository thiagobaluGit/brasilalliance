package me.ramswaroop.jbot.core.slack.db;

import java.util.ArrayList;
import java.util.LinkedList;

import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;

import org.apache.commons.lang.builder.ReflectionToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

@Entity(name = "Raid")
@Table(name = "RAID")
public class RaidEntity {
	
	@EmbeddedId
	private RaidEntityPK raidEntityPK;
	
	private ArrayList<String> usersAttacked;
	
	private LinkedList<String> usersOnList;
	
	public RaidEntity() {}

	public ArrayList<String> getUsersAttacked() {
		return usersAttacked;
	}

	public void setUsersAttacked(ArrayList<String> usersAttacked) {
		this.usersAttacked = usersAttacked;
	}

	public LinkedList<String> getUsersOnList() {
		return usersOnList;
	}

	public void setUsersOnList(LinkedList<String> usersOnList) {
		this.usersOnList = usersOnList;
	}
	
	public RaidEntityPK getRaidEntityPK() {
		return raidEntityPK;
	}

	public void setRaidEntityPK(RaidEntityPK raidEntityPK) {
		this.raidEntityPK = raidEntityPK;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return ReflectionToStringBuilder.toString(this, ToStringStyle.MULTI_LINE_STYLE);
	}
}
