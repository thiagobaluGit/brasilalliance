package me.ramswaroop.jbot.core.slack.db;

import java.util.Date;

import javax.transaction.Transactional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

@Transactional
public interface RaidDAO extends CrudRepository<RaidEntity, Long> {
	
	@Query("SELECT c FROM Raid c WHERE c.raidEntityPK.channelId = ?1 and c.raidEntityPK.datetime = ?2")
	public RaidEntity getRaidByChannelIdAndDatetime(String channelId, Date datetime);
	
}
