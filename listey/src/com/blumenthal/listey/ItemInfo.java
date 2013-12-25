/**
 * 
 */
package com.blumenthal.listey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;

public class ItemInfo {
	public static final String KIND = "item";//kind in the datastore
	public static final String NAME = "name";
	public static final String STATUS = "status";
	public static final String COUNT = "count";
	public static final String LAST_UPDATE = "lastUpdate";
	public static final String UNIQUE_ID = "uniqueId";
	public static final String CATEGORIES = "categories";
	
	public static enum ItemStatus {
	    ACTIVE,
	    COMPLETED
	}
	
	public String name;
	public String uniqueId;
	public Long count = 1L;
	public ItemStatus status;
	public Map<String, ItemCategoryInfo> categories = new HashMap<String, ItemCategoryInfo>();
	public Long lastUpdate;
	
	/** Default constructor */
	public ItemInfo(){}
	
	
	public ItemInfo(Entity entity) {
		if (!entity.getKind().equals(KIND)){
			//check the entity type and throw if not what we're expecting
			throw new IllegalStateException("The constructor was called with an entity of the wrong kind.");
		}//if unexpected kind
		
		lastUpdate = (Long) entity.getProperty(LAST_UPDATE);
		name = (String) entity.getProperty(NAME);
		uniqueId = (String) entity.getKey().getName();
		count = (Long) entity.getProperty(COUNT);
		status = ItemStatus.valueOf((String) entity.getProperty(STATUS));
	}//ItemInfo(Entity)
	
	
	
	/**
	 * @param parent
	 * @return an entity that represents this object (but not its child objects)
	 */
	public Entity toEntity(Key parent) {
		Entity entity = new Entity(KIND, uniqueId, parent);
		entity.setProperty(STATUS, status.toString());
		entity.setProperty(NAME, name);
		entity.setProperty(COUNT,  count);
		entity.setProperty(LAST_UPDATE, lastUpdate);
		return entity;
	}//toEntity
	
	
	
	/** Returns a list of all entities for this object and all sub-objects
	 * 
	 * @param parent
	 * @return
	 */
	public List<Entity> toEntities(Key parent) {
		List<Entity> entities = new ArrayList<Entity>();
		Entity thisEntity = toEntity(parent);
		entities.add(thisEntity);
		for (Map.Entry<String, ItemCategoryInfo> entry : categories.entrySet()) {
			entities.add(entry.getValue().toEntity(thisEntity.getKey()));
		}//foreach category
		return entities;
	}//toEntities
	
	
	/**
	 * @param other
	 * @return Returns true if all essential fields of this object
	 * are the same as other.
	 */
	public boolean shallowEquals(ItemInfo other) {
		return (uniqueId.equals(other.uniqueId)
				&& name.equals(other.name)
				&& lastUpdate.equals(other.lastUpdate)
				&& status.equals(other.status)
				&& count.equals(other.count));
	}//shallowEquals
	
	
	/**
	 * @param other
	 * @return Returns true if this object is essentially the same
	 * as other, and all sub-objects are also.
	 */
	public boolean deepEquals(ItemInfo other) {
		if (!shallowEquals(other)
			|| categories.size() != other.categories.size()) {
			return false;
		}
		for (Map.Entry<String, ItemCategoryInfo> entry : categories.entrySet()) {
			ItemCategoryInfo otherCat = other.categories.get(entry.getKey());
			if (!entry.getValue().deepEquals(otherCat)) {
				return false;
			}
		}//foreach category
		
		//If we get to here, everything is equal
		return true;
	}//deepEquals
}//ItemInfo