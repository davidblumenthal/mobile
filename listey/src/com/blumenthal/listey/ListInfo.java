/**
 * Stores info about a list, e.g. a shopping list of items.
 */
package com.blumenthal.listey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.memcache.InvalidValueException;
import com.google.gson.Gson;

public class ListInfo {
	public static final String KIND = "list";//kind in the datastore
	public static final String NAME = "name";//name in the datastore
	public static final String LAST_UPDATE = "lastUpdate";//lastUpdate in the datastore
	public static final String STATUS = "status";//status in the datastore
	public static final String ITEMS = "items";
	public static final String CATEGORIES = "categories";
	public static final String SELECTED_CATEGORIES = "selectedCategories";
	public static final String OTHER_USER_PRIVS = "otherUserPrivs";
	
	public static enum ListInfoStatus {
		ACTIVE,
		DELETED
	}
	
	private ListInfoStatus status;
	
	private String uniqueId;
	private String name;
	private Map<String, ItemInfo> items = new HashMap<String, ItemInfo>();
	private SortedSet<CategoryInfo> categories = new TreeSet<CategoryInfo>();
	private Long lastUpdate;
	//Note, selectedCategories is not stored on the server, always just mirrored back from the request
	private Set<String> selectedCategories = new HashSet<String>();
	
	private Map<String, OtherUserPrivOnList> otherUserPrivs = new HashMap<String, OtherUserPrivOnList>();
	
	/** Default constructor */
	public ListInfo(){}
	
	/**
	 * @param status
	 * @param uniqueId
	 * @param name
	 * @param items
	 * @param categories
	 * @param lastUpdate
	 * @param selectedCategories
	 */
	public ListInfo(ListInfo.ListInfoStatus status, String uniqueId, String name,
			Long lastUpdate) {
		super();
		this.setStatus(status);
		this.setUniqueId(uniqueId);
		this.setName(name);
		this.setLastUpdate(lastUpdate);
	}

	/** Constructor that makes a shallow copy of only the top-level fields
	 * 
	 * @param source
	 */
	public ListInfo(ListInfo source) {
		this(source.getStatus(), source.getUniqueId(), source.getName(), source.getLastUpdate());
	}
	
	public ListInfo(Entity entity) {
		if (!entity.getKind().equals(KIND)){
			//check the entity type and throw if not what we're expecting
			throw new IllegalStateException("The constructor was called with an entity of the wrong kind.");
		}//if unexpected kind
		setName((String) entity.getProperty(NAME));
		setUniqueId((String) entity.getKey().getName());
		setLastUpdate((Long) entity.getProperty(LAST_UPDATE));
		setStatus(ListInfoStatus.valueOf((String) entity.getProperty(STATUS)));
	}//ListInfo(Entity)
	
	
	
	
	/**
	 * @return the status
	 */
	public ListInfoStatus getStatus() {
		return status;
	}

	/**
	 * @param status the status to set
	 */
	public void setStatus(ListInfoStatus status) {
		this.status = status;
	}

	/**
	 * @return the uniqueId
	 */
	public String getUniqueId() {
		return uniqueId;
	}

	/**
	 * @param uniqueId the uniqueId to set
	 */
	public void setUniqueId(String uniqueId) {
		this.uniqueId = uniqueId;
	}

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @return the items
	 */
	public Map<String, ItemInfo> getItems() {
		return items;
	}

	/**
	 * @param items the items to set
	 */
	public void setItems(Map<String, ItemInfo> items) {
		this.items = items;
	}

	/**
	 * @return the categories
	 */
	public SortedSet<CategoryInfo> getCategories() {
		return categories;
	}

	/**
	 * @param categories the categories to set
	 */
	public void setCategories(SortedSet<CategoryInfo> categories) {
		this.categories = categories;
	}

	/**
	 * @return the lastUpdate
	 */
	public Long getLastUpdate() {
		return lastUpdate;
	}

	/**
	 * @param lastUpdate the lastUpdate to set
	 */
	public void setLastUpdate(Long lastUpdate) {
		this.lastUpdate = lastUpdate;
	}

	/**
	 * @return the selectedCategories
	 */
	public Set<String> getSelectedCategories() {
		return selectedCategories;
	}

	/**
	 * @param selectedCategories the selectedCategories to set
	 */
	public void setSelectedCategories(Set<String> selectedCategories) {
		this.selectedCategories = selectedCategories;
	}

	/**
	 * @return the otherUserPrivs
	 */
	public Map<String, OtherUserPrivOnList> getOtherUserPrivs() {
		return otherUserPrivs;
	}

	/**
	 * @param otherUserPrivs the otherUserPrivs to set
	 */
	public void setOtherUserPrivs(Map<String, OtherUserPrivOnList> otherUserPrivs) {
		this.otherUserPrivs = otherUserPrivs;
	}

	/** Copy constructor using json serialization */
	public static ListInfo makeCopy(ListInfo orig) {
		Gson gson = ListeyDataMultipleUsers.getGson();
		String json = gson.toJson(orig);
		ListInfo copy = gson.fromJson(json, orig.getClass());
		return copy;
	}//makeCopy

	
	
	public Entity toEntity(DataStoreUniqueId uniqueIdCreator, Key parent) {
		//Before converting this to an entity, change the id to a permanent if it's not already
		setUniqueId(uniqueIdCreator.ensurePermanentId(getUniqueId()));
		Entity entity = new Entity(KIND, getUniqueId(), parent);
		entity.setProperty(STATUS, getStatus().toString());
		entity.setProperty(NAME, getName());
		entity.setProperty(LAST_UPDATE, getLastUpdate());
		return entity;
	}//toEntity
	
	
	
	/** 
	 * @param parent entity key
	 * @return a list of all entities for this object and all sub-objects
	 */
	public List<Entity> toEntities(DataStoreUniqueId uniqueIdCreator, Key parent) {
		List<Entity> entities = new ArrayList<Entity>();
		Entity thisEntity = toEntity(uniqueIdCreator, parent);
		Key thisKey = thisEntity.getKey();
		entities.add(thisEntity);
		for (Map.Entry<String, ItemInfo> entry : getItems().entrySet()) {
			entities.addAll(entry.getValue().toEntities(uniqueIdCreator, thisKey));
		}//foreach item
		for (CategoryInfo cat : getCategories()) {
			entities.add(cat.toEntity(thisKey));
		}//foreach category
		for (Map.Entry<String, OtherUserPrivOnList> entry : getOtherUserPrivs().entrySet()) {
			//note, this has no auto-generated uniqueId, so it doesn't need uniqueIdCreator
			entities.add(entry.getValue().toEntity(thisKey, entry.getKey()));
		}
		
		return entities;
	}//toEntities
	
	
	
	/**
	 * @param other
	 * @return Returns true if all essential fields of this object
	 * are the same as other.
	 */
	public boolean shallowEquals(ListInfo other) {
		return (getUniqueId().equals(other.getUniqueId())
				&& getName().equals(other.getName())
				&& getLastUpdate().equals(other.getLastUpdate())
				&& getStatus().equals(other.getStatus()));
	}//shallowEquals
	
	
	/**
	 * @param other
	 * @return Returns true if this object is essentially the same
	 * as other, and all sub-objects are also the same
	 * (EXCEPT selectedCategories, since
	 * that is ephemeral we don't compare it)
	 */
	public boolean deepEquals(ListInfo other) {
		if (!shallowEquals(other)
			|| getCategories().size() != other.getCategories().size()
			|| getItems().size() != other.getItems().size()
			|| getOtherUserPrivs().size() != other.getOtherUserPrivs().size()) {
			return false;
		}
		
		Iterator<CategoryInfo> catIter = getCategories().iterator();
		Iterator<CategoryInfo> otherCatIter = other.getCategories().iterator();
		while (catIter.hasNext()) {
			CategoryInfo cat = catIter.next();
			CategoryInfo otherCat = otherCatIter.next();
			if (!cat.deepEquals(otherCat)) {
				return false;
			}
		}//foreach category
		
		for (Map.Entry<String, ItemInfo> entry : getItems().entrySet()) {
			ItemInfo otherItem = other.getItems().get(entry.getKey());
			if (!entry.getValue().deepEquals(otherItem)) {
				return false;
			}
		}//foreach item
		
		for (Map.Entry<String, OtherUserPrivOnList> entry : getOtherUserPrivs().entrySet()) {
			OtherUserPrivOnList otherPriv = other.getOtherUserPrivs().get(entry.getKey());
			if (!entry.getValue().deepEquals(otherPriv)) {
				return false;
			}
		}//foreach otherUserPrivs
		
		//If we get to here, everything is equal
		return true;
	}//deepEquals
	
	
	
	public static ListInfo compareAndUpdate(DataStoreUniqueId uniqueIdCreator, Key parent, ListInfo serverList, ListInfo clientList,
			List<Entity> updateEntities, List<Entity> deleteEntities) {
		ListInfo rv = null;
		
		//New from the client
		if (serverList == null) {
			if (clientList.getStatus().equals(ListInfo.ListInfoStatus.ACTIVE)) {
				rv = ListInfo.makeCopy(clientList);
				updateEntities.addAll(rv.toEntities(uniqueIdCreator, parent));
			}//new list
			else {
				//deleted on client, don't add to server, nothing to do
			}
		}//serverList == null

		//New from the server
		else if (clientList == null) {
			rv = ListInfo.makeCopy(serverList);
			//no need to update any entities on the server, just the client
		}//clientList == null
		
		else {//both lists already exist
			//use the most recent top-level object, or the client version if they're the same
			if (clientList.shallowEquals(serverList)) {
				rv = ListInfo.makeCopy(clientList);
			} else {
				ListInfo newer = serverList.getLastUpdate() > clientList.getLastUpdate() ? serverList : clientList;
				rv = ListInfo.makeCopy(newer);
				updateEntities.add(rv.toEntity(uniqueIdCreator, parent));
			}

			//Now compare and update each sub-level object
		}//neither list is null
		
		return rv;
	}//compareAndUpdate
}//ListInfo