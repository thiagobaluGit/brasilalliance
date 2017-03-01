package jbot.slack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import jbot.slack.util.UtilSlack;
import me.ramswaroop.jbot.core.slack.Bot;
import me.ramswaroop.jbot.core.slack.Controller;
import me.ramswaroop.jbot.core.slack.EventType;
import me.ramswaroop.jbot.core.slack.db.RaidDAO;
import me.ramswaroop.jbot.core.slack.db.RaidEntity;
import me.ramswaroop.jbot.core.slack.db.RaidEntityPK;
import me.ramswaroop.jbot.core.slack.models.Event;
import me.ramswaroop.jbot.core.slack.models.Message;

/**
 * A Slack Bot sample. You can create multiple bots by just
 * extending {@link Bot} class like this one.
 *
 * Changed to 
 *
 * @author ramswaroop, thiagobalu
 * @version 1.0.0, 05/06/2016
 */
@Component
public class SlackBot extends Bot {
	
	@Autowired
	private RaidDAO raidDao;
	
	private RaidEntity raidEntity;
	
	private boolean alreadyFilledLists = false;

    private int amountOfMinutesToRemindAttack = 5;
    
    private int amountOfMinutesToRemoveFromAttack = 15;
    
    private LinkedList<String> raidListOrder = new LinkedList<String>();
    
    private ArrayList<String> raidListOrderAlreadyAttacked = new ArrayList<String>();
    
    private Timer reminder = new Timer();
    
    private Timer reminderToRemove = new Timer();
    
    
    private ArrayList<String> help = new ArrayList<String>() {/**
		 * 
		 */
		private static final long serialVersionUID = 4889037669770108785L;

	{
        add("*add* : Se Adiciona na Lista");
        add("*done*: Avisa que o ataque acabou");
        add("*remove* : Sai da lista");
        add("*list* : Verifica o estado atual da lista");
        add("*listA* : Verifica quem já atacou");
        add("*skip* : Dá a vez para o próximo membro");
        add("*indo* : Avisa que está atacando e para o timer de notificação");
        add("*clean* : Limpa a lista (Admin apenas)");
        add("*add user position* : Adiciona um user específico em uma posição específica (Admin apenas). Ex.: add user 1 (vai adicionar o usuário user na primeira posição da fila)");
        add("*remove user* : Remove um user específico (Admin apenas). Ex.: remove user");
        add("*timeout time* : Altera o tempo do lembrete (Admin apenas). Ex.: timeout 1 (neste exemplo, muda para avisar a cada minuto)");
        add("*timeoutRemove time* : Altera o tempo do lembrete (Admin apenas). Ex.: timeoutRemove 20 (neste exemplo, muda para remover automaticamente após 20 minutos)");
        add("*help* : exibe essa lista de comandos");
    }};
    
    private ArrayList<String> adminList = new ArrayList<String>() {/**
		 * 
		 */
		private static final long serialVersionUID = -3058542732651255723L;
	{
        add("balu");
        add("jamesc");
        add("ragor");
        add("lucasflx");
        add("coresothenc");
    }};
    /**
     * Slack token from application.properties file. You can get your slack token
     * next <a href="https://my.slack.com/services/new/bot">creating a new bot</a>.
     */
    @Value("${slackBotToken}")
    private String slackToken;

    @Override
    public String getSlackToken() {
        return slackToken;
    }

    @Override
    public Bot getSlackBot() {
        return this;
    }
    
    /**
     * Invoked when the bot receives a direct mention (@botname: message)
     * or a direct message. NOTE: These two event types are added by jbot
     * to make your task easier, Slack doesn't have any direct way to
     * determine these type of events.
     *
     * @param session
     * @param event
     */
    @Controller(events = {EventType.DIRECT_MENTION, EventType.DIRECT_MESSAGE})
    public void onReceiveDM(WebSocketSession session, Event event) {
        reply(session, event, new Message("Ola, eu nao sou o Goku mas sou o bot: " + slackService.getCurrentUser().getName() + " para descobrir quais comandos tenho disponíveis basta digitar: help"));
    }

    /**
     * Invoked when someone types add, than retrieve that message and add user on list.
     * @param session
     * @param event
     * @param matcher
     */
    @Controller(events = EventType.MESSAGE, pattern = "^add$", caseon = false)
    public void onAdd(WebSocketSession session, Event event, Matcher matcher) {
    	retrieveUsersPreviousList(event.getChannelId());
    	if(raidListOrder.contains(event.getUser().getName())) {
    		reply(session, event, new Message(UtilSlack.retrieveNotification(event) + " Você está me testando? Logo saberá o que significa a dor jovem Padawan. " + UtilSlack.DARTH_EMOTICON));
    	} else if(raidListOrderAlreadyAttacked.contains(event.getUser().getName())) {
    		reply(session, event, new Message(UtilSlack.retrieveNotification(event) + " O ataque você já fez jovem Padawan! " + UtilSlack.YODA_EMOTICON));
    	} else {
    		raidListOrder.add(event.getUser().getName());
    		reply(session, event, new Message(UtilSlack.retrieveNotification(event) + " Adicionado você está! " + UtilSlack.YODA_EMOTICON));
    		
    		saveDbPersistence(event.getChannelId());
    		
    		if(raidListOrder.size() == UtilSlack.ONLY_ONE_PADAWAN){
    			setReminderToAttack(event.getUser().getName(), session, event);
    			setReminderToRemove(event.getUser().getName(), session, event);
    		}
    		
    		StringBuffer sb = getListWithoutNotifications(raidListOrder);
    		reply(session, event, new Message("Ordem da fila: " + sb.toString() + " " + UtilSlack.STORMTROOPER_EMOTICON));
    	}
    }

    /**
     * Invoked when someone types indo, than retrieve that message and remove both timer for that user.
     * @param session
     * @param event
     * @param matcher
     */
    @Controller(events = EventType.MESSAGE, pattern = "^indo$", caseon = false)
    public void onGoing(WebSocketSession session, Event event, Matcher matcher) {
    	retrieveUsersPreviousList(event.getChannelId());
    	if(!raidListOrder.isEmpty()){
    		if(raidListOrder.getFirst().equals(event.getUser().getName())) {
    			restartReminder();
    			reply(session, event, new Message(UtilSlack.retrievePadawanNameNotification(event) + " que a força esteja com você! " + UtilSlack.YODA_EMOTICON));    		    			    				
    		} else {
    			reply(session, event, new Message(UtilSlack.retrievePadawanNameNotification(event) + UtilSlack.WRONG_TURN_YODA_SPEECH + UtilSlack.YODA_EMOTICON));
    		}
    	} else {
    		reply(session, event, new Message(UtilSlack.retrieveNotification(event) + UtilSlack.EMPTY_LIST + UtilSlack.C3PO_EMOTICON));
    	}
    }

    /**
     * Invoked when someone types skip, than retrieve that message and change the order from list skipping one position for the user.
     * @param session
     * @param event
     * @param matcher
     */
	@Controller(events = EventType.MESSAGE, pattern = "^skip$", caseon = false)
    public void onSkip(WebSocketSession session, Event event, Matcher matcher) {
		retrieveUsersPreviousList(event.getChannelId());
    	if(raidListOrder.size() <= UtilSlack.ONLY_ONE_PADAWAN) {
    		reply(session, event, new Message(UtilSlack.retrieveNotification(event) + " A lista não possui membros suficientes para uma troca! " + UtilSlack.C3PO_EMOTICON));
    	} else if(raidListOrder.contains(event.getUser().getName())) {
    		
    		int position = raidListOrder.indexOf(event.getUser().getName());
    		
    		if(position < raidListOrder.size()-1) {
    			boolean isFirst = raidListOrder.getFirst().equals(event.getUser().getName());
    			//Troca a posição dos elementos
    			Collections.swap(raidListOrder, position, position+1);
    			saveDbPersistence(event.getChannelId());
    			reply(session, event, new Message("Sua vez cedida foi jovem padawan: " + event.getUser().getName() + "! " + UtilSlack.YODA_EMOTICON));
    			// Seta o reminder para o próximo caso quem tenha dado skip era o primeiro
    			if(isFirst){
    				restartReminder();
    				setReminderToAttack(raidListOrder.getFirst(), session, event);
    				setReminderToRemove(raidListOrder.getFirst(), session, event);
    				reply(session, event, new Message("Padawan @" + raidListOrder.getFirst() + " sua vez é! " + UtilSlack.YODA_EMOTICON));
    			}
    		} else {
    			reply(session, event, new Message(UtilSlack.retrieveNotification(event) + " Você é o último da fila, não pode executar esse comando! " + UtilSlack.C3PO_EMOTICON));
    		}
    		
    	} else {
    		reply(session, event, new Message(UtilSlack.retrieveNotification(event) + " Você na fila não está! " + UtilSlack.YODA_EMOTICON));    		
    	}
    }
    
	/**
	 * Invoked when someone types done, than retrieve that message and remove from list to attack and add on other list to know if already attacked.
	 * @param session
	 * @param event
	 * @param matcher
	 */
    @Controller(events = EventType.MESSAGE, pattern = "^done$", caseon = false)
    public void onCompleted(WebSocketSession session, Event event, Matcher matcher) {
    	retrieveUsersPreviousList(event.getChannelId());
    	if(!raidListOrder.isEmpty()){
    		if(raidListOrder.getFirst().equals(event.getUser().getName())) {
    			raidListOrderAlreadyAttacked.add(raidListOrder.removeFirst());
    			saveDbPersistence(event.getChannelId());
    			restartReminder();
    			if(!raidListOrder.isEmpty()){
    				reply(session, event, new Message("Padawan @" + raidListOrder.getFirst()  + " sua vez é! " + UtilSlack.YODA_EMOTICON));
    				setReminderToAttack(raidListOrder.getFirst(), session, event);
    				setReminderToRemove(raidListOrder.getFirst(), session, event);
    			} else {
    				reply(session, event, new Message(UtilSlack.retrieveNotification(event) + " Obrigado! " + UtilSlack.C3PO_EMOTICON));
    			}
    		} else {
    			reply(session, event, new Message(UtilSlack.retrievePadawanNameNotification(event) + UtilSlack.WRONG_TURN_YODA_SPEECH + UtilSlack.YODA_EMOTICON));
    		}
    	} else {
    		reply(session, event, new Message(UtilSlack.retrieveNotification(event) + UtilSlack.EMPTY_LIST + UtilSlack.C3PO_EMOTICON));
    	}
    }
    
    /**
     * Invoked when someone types remove, than retrieve that message and remove the user from the list.
     * @param session
     * @param event
     * @param matcher
     */
    @Controller(events = EventType.MESSAGE, pattern = "^remove$", caseon = false)
    public void onRemove(WebSocketSession session, Event event, Matcher matcher) {
    	retrieveUsersPreviousList(event.getChannelId());
    	boolean wasFirst = raidListOrder.size() >= UtilSlack.ONLY_ONE_PADAWAN && raidListOrder.getFirst().equals(event.getUser().getName());
    	if(raidListOrder.remove(event.getUser().getName())) {
    		reply(session, event, new Message(UtilSlack.retrieveNotification(event) + " Removido da fila você foi! " + UtilSlack.YODA_EMOTICON));
    		saveDbPersistence(event.getChannelId());
    		if(wasFirst) {
    			restartReminder();
    			if(!raidListOrder.isEmpty()){
    				reply(session, event, new Message("Padawan @" + raidListOrder.getFirst()  + " sua vez é! " + UtilSlack.YODA_EMOTICON));
    				setReminderToAttack(raidListOrder.getFirst(), session, event);
    				setReminderToRemove(raidListOrder.getFirst(), session, event);
    			}
    		}
    	} else {
    		reply(session, event, new Message(UtilSlack.retrieveNotification(event) + " Para ser removido primeira precisa se cadastrar seu tolo! " + UtilSlack.DARTH_EMOTICON));
    	}
        
    }
    
    /**
     * Invoked when someone types list, than print the current list to attack.
     * @param session
     * @param event
     * @param matcher
     */
    @Controller(events = EventType.MESSAGE, pattern = "^list$", caseon = false)
    public void onList(WebSocketSession session, Event event, Matcher matcher) {
    	retrieveUsersPreviousList(event.getChannelId());
    	if(raidListOrder.isEmpty()) {
    		reply(session, event, new Message(UtilSlack.retrieveNotification(event) + UtilSlack.EMPTY_LIST_YODA_SPEECH + UtilSlack.YODA_EMOTICON));
    	} else {
    		StringBuffer sb = getListWithoutNotifications(raidListOrder);
    		reply(session, event, new Message("Ordem da fila: " + sb.toString() + " " + UtilSlack.STORMTROOPER_EMOTICON));    		
    	}
    }
    
    /**
     * Invoked when someone types lista, than print the current list with users that already attacked.
     * @param session
     * @param event
     * @param matcher
     */
    @Controller(events = EventType.MESSAGE, pattern = "^listA$", caseon = false)
    public void onListAttack(WebSocketSession session, Event event, Matcher matcher) {
    	retrieveUsersPreviousList(event.getChannelId());
    	if(raidListOrderAlreadyAttacked.isEmpty()) {
    		reply(session, event, new Message(UtilSlack.retrieveNotification(event) + UtilSlack.EMPTY_LIST_YODA_SPEECH + UtilSlack.YODA_EMOTICON));
    	} else {
    		StringBuffer sb = getListWithoutNotifications(raidListOrderAlreadyAttacked);
    		reply(session, event, new Message("Quem já atacou: " + sb.toString() + " " + UtilSlack.STORMTROOPER_EMOTICON));    		
    	}
    }

    /**
     * Invoked when someone types help, than print all available commands.
     * @param session
     * @param event
     * @param matcher
     */
    @Controller(events = EventType.MESSAGE, pattern = "^help$", caseon = false)
    public void onHelp(WebSocketSession session, Event event, Matcher matcher) {
    	StringBuffer sb = new StringBuffer();
    	sb.append("Comandos para o jovem padawan: " + UtilSlack.YODA_EMOTICON);
    	for (int i = 0; i < help.size(); i++) {
    		sb.append("\n");
    		sb.append((i+1) + ". ");
    		sb.append(help.get(i));
		}
        reply(session, event, new Message(sb.toString()));
    }
    
    /**
     * Invoked when admin types clean, than clean both lists and save on database.
     * @param session
     * @param event
     * @param matcher
     */
    @Controller(events = EventType.MESSAGE, pattern = "^clean$", caseon = false)
    public void onClean(WebSocketSession session, Event event, Matcher matcher) {
    	retrieveUsersPreviousList(event.getChannelId());
    	if(adminList.contains(event.getUser().getName())) {
    		restartReminder();
    		reply(session, event, new Message("A fila foi resetada master " + event.getUser().getName() + "! " + UtilSlack.R2D2_EMOTICON));
    		StringBuffer sb = getListWithoutNotifications(raidListOrderAlreadyAttacked);
    		reply(session, event, new Message("Padawans que atacaram nessa raid: " + sb.toString() + "! " + UtilSlack.R2D2_EMOTICON));
    		raidListOrder.clear();
    		raidListOrderAlreadyAttacked.clear();
    		saveDbPersistence(event.getChannelId());
    	} else {
    		reply(session, event, new Message(UtilSlack.retrieveNotification(event) + UtilSlack.NOT_ALLOWED_ORDER + UtilSlack.STORMTROOPER_EMOTICON));
    	}
    }
    
    /**
     * Invoked when admin types timeout, reset the reminder to attack with the value specified.
     * @param session
     * @param event
     * @param matcher
     */
    @Controller(events = EventType.MESSAGE, pattern = "^timeout [0-9]+$", caseon = false)
    public void onTimeout(WebSocketSession session, Event event, Matcher matcher) {
    	retrieveUsersPreviousList(event.getChannelId());
    	if(adminList.contains(event.getUser().getName())) {
    		amountOfMinutesToRemindAttack = Integer.parseInt(matcher.group(0).replace("timeout ", ""));
    		if(amountOfMinutesToRemindAttack > amountOfMinutesToRemoveFromAttack){
    			reply(session, event, new Message("O tempo de notificação da fila deve ser menor do que o tempo de remoção master " + event.getUser().getName() + "! " + UtilSlack.R2D2_EMOTICON));
    			reply(session, event, new Message(String.format("Tempos configurados: tempo de lembrete [%d] tempo para remoção [%d]", amountOfMinutesToRemindAttack, amountOfMinutesToRemoveFromAttack) + UtilSlack.R2D2_EMOTICON));
    		} else {
    			restartReminder();
    			reply(session, event, new Message("O tempo de notificação da fila foi alterado master " + event.getUser().getName() + "! " + UtilSlack.R2D2_EMOTICON));
    			//Reseta o valor
    			if(!raidListOrder.isEmpty()){
    				setReminderToAttack(raidListOrder.getFirst(), session, event);
    				setReminderToRemove(raidListOrder.getFirst(), session, event);
    			}
    		}
    	} else {
    		reply(session, event, new Message(UtilSlack.retrieveNotification(event) + UtilSlack.NOT_ALLOWED_ORDER + UtilSlack.STORMTROOPER_EMOTICON));
    	}
    }
    
    /**
     * Invoked when admin types timeoutremove, reset the reminder to remove from list with the value specified.
     * @param session
     * @param event
     * @param matcher
     */
    @Controller(events = EventType.MESSAGE, pattern = "^timeoutRemove [0-9]+$", caseon = false)
    public void onTimeoutRemove(WebSocketSession session, Event event, Matcher matcher) {
    	retrieveUsersPreviousList(event.getChannelId());
    	if(adminList.contains(event.getUser().getName())) {
    		amountOfMinutesToRemoveFromAttack = Integer.parseInt(matcher.group(0).replace("timeoutRemove ", ""));
    		if(amountOfMinutesToRemoveFromAttack < amountOfMinutesToRemindAttack){
    			reply(session, event, new Message("O tempo de remoção automática da fila deve ser maior do que o tempo de notificação master " + event.getUser().getName() + "! " + UtilSlack.R2D2_EMOTICON));
    			reply(session, event, new Message(String.format("Tempos configurados: tempo de lembrete [%d] tempo para remoção [%d]", amountOfMinutesToRemindAttack, amountOfMinutesToRemoveFromAttack) + UtilSlack.R2D2_EMOTICON));
    		} else {
    			restartReminder();
    			reply(session, event, new Message("O tempo para remoção da fila foi alterado master " + event.getUser().getName() + "! " + UtilSlack.R2D2_EMOTICON));
    			//Reseta o valor
    			if(!raidListOrder.isEmpty()){
    				setReminderToAttack(raidListOrder.getFirst(), session, event);
    				setReminderToRemove(raidListOrder.getFirst(), session, event);
    			}
    		}
    	} else {
    		reply(session, event, new Message(UtilSlack.retrieveNotification(event) + UtilSlack.NOT_ALLOWED_ORDER + UtilSlack.STORMTROOPER_EMOTICON));
    	}
    }
    
    /**
     * Invoked when admin types remove user, search for the specified user than remove it
     * @param session
     * @param event
     * @param matcher
     */
    @Controller(events = EventType.MESSAGE, pattern = "^remove [a-zA-Z0-9]+$", caseon = false)
    public void onRemoveSpecificUser(WebSocketSession session, Event event, Matcher matcher) {
    	retrieveUsersPreviousList(event.getChannelId());
    	if(adminList.contains(event.getUser().getName())) {
    		String user = matcher.group(0).replace("remove ", "");
    		removeSpecificUser(session, event, user);
    	} else {
    		reply(session, event, new Message(UtilSlack.retrieveNotification(event) + UtilSlack.NOT_ALLOWED_ORDER + UtilSlack.STORMTROOPER_EMOTICON));
    	}
    }

    /**
     * Invoked when admin types add user position, insert the user specified on the position
     * @param session
     * @param event
     * @param matcher
     */
    @Controller(events = EventType.MESSAGE, pattern = "^add [a-zA-Z0-9]+ [0-9]+$", caseon = false)
    public void onAddSpecificUser(WebSocketSession session, Event event, Matcher matcher) {
    	retrieveUsersPreviousList(event.getChannelId());
    	if(adminList.contains(event.getUser().getName())) {
    		String[] informations = matcher.group(0).split(" ");
    		// Apenas por segurança
    		if(informations != null && informations.length > 2) {
    			String user = informations[1];
    			int position = Integer.parseInt(informations[2]);
    			if(raidListOrder.contains(user)){
    				reply(session, event, new Message(UtilSlack.retrieveNotification(event) + " padawan " + user +" já está na fila! " + UtilSlack.R2D2_EMOTICON));
    			} else {
    				// Condição para não permitir inserir em uma posição inexistente ou muito longe
    				if(position > raidListOrder.size()+1 || position == 0) {
    					reply(session, event, new Message("Não é possível adicionar nessa posição master " + event.getUser().getName() + "! " + UtilSlack.R2D2_EMOTICON));
    				} else {
    					raidListOrder.add(position-1, user);
    					boolean isNewFirst = raidListOrder.size() >= UtilSlack.ONLY_ONE_PADAWAN && raidListOrder.getFirst().equals(user);
    					if(isNewFirst) {
    						restartReminder();
    						reply(session, event, new Message("Padawan @" + raidListOrder.getFirst()  + " sua vez é! " + UtilSlack.YODA_EMOTICON));
    						setReminderToAttack(raidListOrder.getFirst(), session, event);
    						setReminderToRemove(raidListOrder.getFirst(), session, event);
    					}
    					saveDbPersistence(event.getChannelId());
    				}
    			}
    		} else {
				reply(session, event, new Message(UtilSlack.retrieveNotification(event) + " ocorreu um erro ao inserir o novo padawan na lista! " + UtilSlack.R2D2_EMOTICON));
			}
    	} else {
    		reply(session, event, new Message(UtilSlack.retrieveNotification(event) + UtilSlack.NOT_ALLOWED_ORDER + UtilSlack.STORMTROOPER_EMOTICON));
    	}
    }
    
    /**
     * Retrieve from database all raid informations
     * @param channelId
     */
    private void retrieveUsersPreviousList(String channelId){
    	System.out.println("Retrieving list created before");
		
		if(!alreadyFilledLists) {
			System.out.println("It is first execution");
			RaidEntity re = raidDao.getRaidByChannelIdAndDatetime(channelId, new Date());
			if(re == null) {
				System.out.println("Creating new RaidEntity");
				raidEntity = new RaidEntity();
				RaidEntityPK rpk = new RaidEntityPK();
				rpk.setDatetime(new Date());
				rpk.setChannelId(channelId);
				raidEntity.setUsersAttacked(raidListOrderAlreadyAttacked);
				raidEntity.setUsersOnList(raidListOrder);
				raidEntity.setRaidEntityPK(rpk);
				System.out.println("Entidade criada : " + raidEntity);
			} else {
				raidEntity = re;
				raidListOrderAlreadyAttacked = re.getUsersAttacked();
				raidListOrder = re.getUsersOnList();
			}
			alreadyFilledLists = true;
		}
    }

    /**
     * Persist on database raid informations
     * @param channelId
     */
	private void saveDbPersistence(String channelId) {
		System.out.println("Persisting added User");
		
		raidEntity.setUsersAttacked(raidListOrderAlreadyAttacked);
		raidEntity.setUsersOnList(raidListOrder);
		
		raidDao.save(raidEntity);
		
		System.out.println("Persisted with success :" + raidEntity);
	}
    
	/**
	 * Remove specific user from list and persist on database
	 * @param session
	 * @param event
	 * @param user
	 */
    private void removeSpecificUser(WebSocketSession session, Event event, String user) {
		boolean wasFirst = raidListOrder.size() >= UtilSlack.ONLY_ONE_PADAWAN && raidListOrder.getFirst().equals(user);
		if(raidListOrder.remove(user)) {
			reply(session, event, new Message(UtilSlack.retrieveNotification(event) + " padawan " + user +" removido da fila foi! " + UtilSlack.YODA_EMOTICON));
			if(wasFirst) {
				restartReminder();
				if(!raidListOrder.isEmpty()){
					reply(session, event, new Message("Padawan @" + raidListOrder.getFirst()  + " sua vez é! " + UtilSlack.YODA_EMOTICON));
					setReminderToAttack(raidListOrder.getFirst(), session, event);
					setReminderToRemove(raidListOrder.getFirst(), session, event);
				}
			}
			saveDbPersistence(event.getChannelId());
		} else {
			reply(session, event, new Message(UtilSlack.retrieveNotification(event) + " padawan " + user +" não encontrado na lista! " + UtilSlack.R2D2_EMOTICON));
		}
	}
    
    /**
     * Creates an output message with a list to print
     * @param list
     * @return
     */
    private StringBuffer getListWithoutNotifications(List<String> list) {
		StringBuffer sb = new StringBuffer();
		sb.append("[");
		for (String string : list) {
			sb.append(UtilSlack.DELIMITER_AVOID_NOTIFICATION);
			sb.append(string);
			sb.append(UtilSlack.DELIMITER_AVOID_NOTIFICATION);
			sb.append(",");
		}
		if(!list.isEmpty()) {
			sb.deleteCharAt(sb.length()-1);			
		}
		sb.append("]");
		return sb;
	}
    
    /**
     * Creates a TimerTask to remember the user to attack
     * @param padawanName
     * @param session
     * @param event
     */
    private void setReminderToAttack(final String padawanName, final WebSocketSession session, final Event event) {
    	reminder.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
            	reply(session, event, new Message(UtilSlack.retrieveNotification(event) + " Soldado, é a sua vez de atacar! " + UtilSlack.STORMTROOPER_EMOTICON));
            }
        }, amountOfMinutesToRemindAttack*60*1000, amountOfMinutesToRemindAttack*60*1000);
	}
    
    /**
     * Creates a TimerTask to remove automatically the user from list
     * @param padawanName
     * @param session
     * @param event
     */
    private void setReminderToRemove(final String padawanName, final WebSocketSession session, final Event event) {
    	reminderToRemove.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
            	reply(session, event, new Message(UtilSlack.retrieveNotification(event) + " Soldado, seu tempo acabou! " + UtilSlack.STORMTROOPER_EMOTICON));
            	removeSpecificUser(session, event, padawanName);
            }
        }, amountOfMinutesToRemoveFromAttack*60*1000, amountOfMinutesToRemoveFromAttack*60*1000);
	}
    
    /**
     * Restart all reminders
     */
    private void restartReminder() {
		reminder.cancel();
		reminder = new Timer();
		reminderToRemove.cancel();
		reminderToRemove = new Timer();
	}
//    /**
//     * Invoked when bot receives an event of type message with text satisfying
//     * the pattern {@code ([a-z ]{2})(\d+)([a-z ]{2})}. For example,
//     * messages like "ab12xy" or "ab2bc" etc will invoke this method.
//     *
//     * @param session
//     * @param event
//     */
//    @Controller(events = EventType.MESSAGE, pattern = "^([a-z ]{2})(\\d+)([a-z ]{2})$")
//    public void onReceiveMessage(WebSocketSession session, Event event, Matcher matcher) {
//        reply(session, event, new Message("First group: " + matcher.group(0) + "\n" +
//                "Second group: " + matcher.group(1) + "\n" +
//                "Third group: " + matcher.group(2) + "\n" +
//                "Fourth group: " + matcher.group(3)));
//    }
//    
//    

//    /**
//     * Invoked when an item is pinned in the channel.
//     *
//     * @param session
//     * @param event
//     */
//    @Controller(events = EventType.PIN_ADDED)
//    public void onPinAdded(WebSocketSession session, Event event) {
//        reply(session, event, new Message("Thanks for the pin! You can find all pinned items under channel details."));
//    }
//
//    /**
//     * Invoked when bot receives an event of type file shared.
//     * NOTE: You can't reply to this event as slack doesn't send
//     * a channel id for this event type. You can learn more about
//     * <a href="https://api.slack.com/events/file_shared">file_shared</a>
//     * event from Slack's Api documentation.
//     *
//     * @param session
//     * @param event
//     */
//    @Controller(events = EventType.FILE_SHARED)
//    public void onFileShared(WebSocketSession session, Event event) {
//        logger.info("File shared: {}", event);
//    }
//
//
//    /**
//     * Conversation feature of JBot. This method is the starting point of the conversation (as it
//     * calls {@link Bot#startConversation(Event, String)} within it. You can chain methods which will be invoked
//     * one after the other leading to a conversation. You can chain methods with {@link Controller#next()} by
//     * specifying the method name to chain with.
//     *
//     * @param session
//     * @param event
//     */
//    @Controller(pattern = "(setup meeting)", next = "confirmTiming")
//    public void setupMeeting(WebSocketSession session, Event event) {
//        startConversation(event, "confirmTiming");   // start conversation
//        reply(session, event, new Message("Cool! At what time (ex. 15:30) do you want me to set up the meeting?"));
//    }
//
//    /**
//     * This method is chained with {@link SlackBot#setupMeeting(WebSocketSession, Event)}.
//     *
//     * @param session
//     * @param event
//     */
//    @Controller(next = "askTimeForMeeting")
//    public void confirmTiming(WebSocketSession session, Event event) {
//        reply(session, event, new Message("Your meeting is set at " + event.getText() +
//                ". Would you like to repeat it tomorrow?"));
//        nextConversation(event);    // jump to next question in conversation
//    }
//
//    /**
//     * This method is chained with {@link SlackBot#confirmTiming(WebSocketSession, Event)}.
//     *
//     * @param session
//     * @param event
//     */
//    @Controller(next = "askWhetherToRepeat")
//    public void askTimeForMeeting(WebSocketSession session, Event event) {
//        if (event.getText().contains("yes")) {
//            reply(session, event, new Message("Okay. Would you like me to set a reminder for you?"));
//            nextConversation(event);    // jump to next question in conversation  
//        } else {
//            reply(session, event, new Message("No problem. You can always schedule one with 'setup meeting' command."));
//            stopConversation(event);    // stop conversation only if user says no
//        }
//    }
//
//    /**
//     * This method is chained with {@link SlackBot#askTimeForMeeting(WebSocketSession, Event)}.
//     *
//     * @param session
//     * @param event
//     */
//    @Controller
//    public void askWhetherToRepeat(WebSocketSession session, Event event) {
//        if (event.getText().contains("yes")) {
//            reply(session, event, new Message("Great! I will remind you tomorrow before the meeting."));
//        } else {
//            reply(session, event, new Message("Oh! my boss is smart enough to remind himself :)"));
//        }
//        stopConversation(event);    // stop conversation
//    }
}