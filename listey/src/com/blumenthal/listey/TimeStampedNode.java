/**
 * 
 */
package com.blumenthal.listey;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.gson.Gson;


/**
 * This is the base class for the nodes in the Listey data hierarchy.
 * It allows us to reuse some of the generic methods in the hierarchy.
 * 
 * @author David
 *
 */
public abstract class TimeStampedNode implements Comparable<TimeStampedNode>{
	private static final Logger log = Logger.getLogger(TimeStampedNode.class.getName());
	
	public static enum Status {
		ACTIVE,
		COMPLETED,
		HIDDEN,
		DELETED
	}
	
	private boolean changedOnServer;
	
	/** 
	 * @return true if a change was pushed from the server and the client should refresh
	 */
	public boolean getChangedOnServer() {
		return changedOnServer;
	}
	
	/**
	 * If we notice that the server has an update for a node that should
	 * be pushed to the client, set this to true for that node.
	 * @param newValue
	 */
	public void setChangedOnServer (boolean newValue) {
		changedOnServer=newValue;
	}
	
	/**
	 * @return the lastUpdate
	 */
	public abstract Long getLastUpdate();

	/**
	 * @return the uniqueId
	 */
	public abstract String getUniqueId();
	

	/**
	 * @return the name field, if supported.  Otherwise null.
	 */
	public String getName(){
		return null;
	}
	
	
	/**
	 * @return the status
	 */
	public abstract Status getStatus();
	
	
	/**
	 * @return the entity kind
	 */
	public abstract String getKind();
	
	
	/**
	 * @param parent
	 * @return the entity key for Entity object corresponding to this object
	 */
	public Key getEntityKey(Key parent) {
		return KeyFactory.createKey(parent, getKind(), getUniqueId());
	}
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TimeStampedNode other = (TimeStampedNode) obj;
		if (getUniqueId() == null) {
			if (other.getUniqueId() != null)
				return false;
		} else if (!getUniqueId().equals(other.getUniqueId()))
			return false;
		return true;
	}
	
    /* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((getUniqueId() == null) ? 0 : getUniqueId().hashCode());
		return result;
	}//hashCode
	
	
	/* (non-Javadoc)
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public int compareTo(TimeStampedNode o) {
		return getUniqueId().compareTo(o.getUniqueId());
	}
	

	/** Make a copy using json serialization */
	public TimeStampedNode makeCopy() {
		//true value means the json string should include all the fields
		Gson gson = ListeyDataMultipleUsers.getGson(true);
		String json = gson.toJson(this);
		TimeStampedNode copy = gson.fromJson(json, this.getClass());
		return copy;
	}//makeCopy
	
	
	public abstract TimeStampedNode makeShallowCopy();
	
	/** 
	 * 
	 * @return This returns an array of Maps. Each of the Lists in the array
	 * is a subList of other TimeSTampedNodes that needs to be compared.
	 * If there are no subMaps, returns null.
	 */
	public List<Map<String, ? extends TimeStampedNode>> subMapsToCompare() {
		return null;
	}
	
	
	
	/** 
	 * 
	 * @return This returns an array of Lists. Each of the Lists in the array
	 * is a subList of other TimeSTampedNodes that needs to be compared.
	 * Returns null if the subclass doesn't even implement lists.
	 */
	public List<Iterable<? extends TimeStampedNode>> subIterablesToCompare() {
		return null;
	}
	
	
	
	/**
	 * If we realize we actually need to add a new subclass map entry, we'll call this method to add it.
	 * Top layer of multidimensional list is for each subIter type.
	 * Next layer is for each item to be added to that type.
	 * Subclasses that have subMaps need to override this to add the params.  This will
	 * always call with the correct number of entries in the top layer in the correct order.
	 * @param subMapEntriesToAdd
	 */
	public void addSubMapEntries(List<List<? extends TimeStampedNode>> subMapEntriesToAdd) {
		//base class does nothing
	}
	
	
	
	/**
	 * If we realize we actually need to add a new subclass iterator entry, we'll call this method to add it.
	 * Top layer of multidimensional list is for each subIter type.
	 * Next layer is for each item to be added to that type.
	 * Subclasses that have subIters need to override this to add the params.  This will
	 * always call with the correct number of entries in the top layer in the correct order.
	 * @param subIterEntriesToAdd
	 */
	public void addSubIterEntries(List<List<? extends TimeStampedNode>> subIterEntriesToAdd) {
		//base class does nothing
	}
	
	
	
	public abstract Entity toEntity(DataStoreUniqueId uniqueIdCreator, Key parent);
	
	
	public void copyEphemeralFromClient(TimeStampedNode client){}
	
	
	/** Default implementation just calls toEntity() and returns that
	 * 
	 * @param uniqueIdCreator
	 * @param parent
	 * @return
	 */
	public List<Entity> toEntities(DataStoreUniqueId uniqueIdCreator, Key parent) {
		List<Entity> rv = new ArrayList<Entity>();
		rv.add(toEntity(uniqueIdCreator, parent));
		return rv;
	}//toEntities
	
	
	public abstract boolean shallowEquals(TimeStampedNode other);
	
	

	public static TimeStampedNode compareAndUpdate(DataStoreUniqueId uniqueIdCreator, Key parent, TimeStampedNode serverObj, TimeStampedNode clientObj,
			Iterable<? extends TimeStampedNode> serverPeers, List<Entity> updateEntities, List<Key> deleteKeys) {
		TimeStampedNode rv = null;
		
		//New from the client
		if (serverObj == null) {
			if (!clientObj.getStatus().equals(Status.DELETED)) {
				//Before we add it, let's look and see if there's something with the same
				//name already on the server.  If so, let's update that instead.
				String clientName = clientObj.getName();
				if (clientName != null) {
					for (TimeStampedNode serverPeer : serverPeers) {
						if (!serverPeer.getStatus().equals(Status.DELETED) && clientName.equals(serverPeer.getName())) {
							//If an item with this name already exists in active state on the server
							//in another node, then assume it's a double-send from the client and just
							//ignore this one rather than resending.
							getLog().warning("New " + clientObj.getKind() + " from client matched existing server item with same name (" + serverPeer.getName() + "), skipping");
							return null;
						}
					}//foreach serverPeer
				}//if clientName
				
				rv = clientObj.makeCopy();
				//If it's new from the client, then we're creating a new unique id for it, so flag it as new from the server
				//Note, all sub-objects under this are also new, but it's hard to flag them as new and not
				//worth the effort.
				rv.setChangedOnServer(true);
				updateEntities.addAll(rv.toEntities(uniqueIdCreator, parent));
				getLog().info("Adding entities for new " + rv.getKind() + " from client " + rv.getUniqueId());
			}//new list
			else {
				//deleted on client, don't add to server, nothing to do
			}
		}//serverList == null

		//New from the server
		else if (clientObj == null) {
			rv = serverObj.makeCopy();
			rv.setChangedOnServer(true);
			//no need to update any entities on the server, just the client
		}//clientList == null
		
		else {//both nodes already exist
			//use the most recent top-level object, or the client version if they're the same
			TimeStampedNode newer;
			if (serverObj.getLastUpdate() > clientObj.getLastUpdate()) {
				newer = serverObj;
				rv = newer.makeShallowCopy();
				rv.setChangedOnServer(true);
			}
			else {
				newer = clientObj;
				rv = newer.makeShallowCopy();
				if (!clientObj.shallowEquals(serverObj)) {
					Entity thisEntity;
					if (rv.getStatus().equals(Status.DELETED)) {
						List<Entity> thisAndChildEntities = newer.toEntities(uniqueIdCreator, parent);
						thisEntity = thisAndChildEntities.remove(0);
						updateEntities.add(thisEntity);
						getLog().info("Adding top entity to update for " + newer.getKind() + " " + newer.getUniqueId() + " deleted on client");
						for (Entity toDelete : thisAndChildEntities) {
							getLog().info("  Adding child entity to delete");
							deleteKeys.add(toDelete.getKey());
						}
						return rv;
					}
					//If the top-level object changed on the client then push it on the update list
					thisEntity = rv.toEntity(uniqueIdCreator, parent);
					if (thisEntity != null) {
						updateEntities.add(thisEntity);
						getLog().info("Adding top entity to update for " + rv.getKind() + " " + rv.getUniqueId() + " updated on client");
					}
				}
			}//client is newer
			
			Key thisEntityKey = rv.getEntityKey(parent);

			//Now compare and update each sub-level object
			//MAPS ####################################################
			List<Map<String, ? extends TimeStampedNode>> clientSubMaps = clientObj.subMapsToCompare();
			if (clientSubMaps != null) {
				List<Map<String, ? extends TimeStampedNode>> serverSubMaps = serverObj.subMapsToCompare();

				List<List<? extends TimeStampedNode>> subMapAddLists = new ArrayList<List<? extends TimeStampedNode>>();
				for (int i=0; i<clientSubMaps.size(); i++) {
					List<TimeStampedNode> subMapEntriesToAdd = new ArrayList<TimeStampedNode>();
					subMapAddLists.add(subMapEntriesToAdd);
					
					Map<String, ? extends TimeStampedNode> clientSubMap = clientSubMaps.get(i);
					Map<String, ? extends TimeStampedNode> serverSubMap = serverSubMaps.get(i);
					

					Set<TimeStampedNode> fullSet = new HashSet<TimeStampedNode>(clientSubMap.values());
					fullSet.addAll(serverSubMap.values());

					for (TimeStampedNode fullSetList : fullSet) {
						TimeStampedNode clientSubObj = clientSubMap.get(fullSetList.getUniqueId());
						TimeStampedNode serverSubObj = serverSubMap.get(fullSetList.getUniqueId());
						
						TimeStampedNode updatedObj = TimeStampedNode.compareAndUpdate(uniqueIdCreator, thisEntityKey, serverSubObj, clientSubObj, serverSubMap.values(), updateEntities, deleteKeys);
						if (updatedObj != null) {
							if (updatedObj.getChangedOnServer()) rv.setChangedOnServer(true);
							subMapEntriesToAdd.add(updatedObj);
						}
					}//for each subObj
				}//for each submap
				rv.addSubMapEntries(subMapAddLists);
			}//if any submaps exist
			
			//ITERABLES ####################################################
			List<Iterable<? extends TimeStampedNode>> clientSubIters = clientObj.subIterablesToCompare();
			if (clientSubIters != null) {
				List<Iterable<? extends TimeStampedNode>> serverSubIters = serverObj.subIterablesToCompare();

				List<List<? extends TimeStampedNode>> subIterAddLists = new ArrayList<List<? extends TimeStampedNode>>();
				for (int i=0; i<clientSubIters.size(); i++) {
					List<TimeStampedNode> subIterEntriesToAdd = new ArrayList<TimeStampedNode>();
					subIterAddLists.add(subIterEntriesToAdd);
					
					Iterator<? extends TimeStampedNode> clientSubIter = clientSubIters.get(i).iterator();
					Iterator<? extends TimeStampedNode> serverSubIter = serverSubIters.get(i).iterator();

					TimeStampedNode clientSubObj = clientSubIter.hasNext() ? clientSubIter.next() : null;
					TimeStampedNode serverSubObj = serverSubIter.hasNext() ? serverSubIter.next() : null;
					while (true) {
						if (clientSubObj == null && serverSubObj == null) break;
						
						TimeStampedNode clientToCompare=clientSubObj, serverToCompare=serverSubObj;
						//Since the iterables are sorted, we know if the client one is more than the server one
						//that the server one is missing, and vice versa
						if (clientSubObj == null || (serverSubObj != null && clientSubObj.compareTo(serverSubObj)>0)) {
							//add server obj to client
							clientToCompare=null;
							
							//Increment server iterator
							serverSubObj = serverSubIter.hasNext() ? serverSubIter.next() : null;
						}
						else if (serverSubObj == null || (clientSubObj != null && serverSubObj.compareTo(clientSubObj)>0)) {
							//add client obj to server
							serverToCompare = null;
							
							//Increment client iterator
							clientSubObj = clientSubIter.hasNext() ? clientSubIter.next() : null;
						}
						else {							
							//Increment both iterators
							serverSubObj = serverSubIter.hasNext() ? serverSubIter.next() : null;
							clientSubObj = clientSubIter.hasNext() ? clientSubIter.next() : null;
						}
						
						//Same object, so compare and update if needed
						TimeStampedNode updatedObj = TimeStampedNode.compareAndUpdate(uniqueIdCreator, thisEntityKey, serverToCompare, clientToCompare, serverSubIters.get(i), updateEntities, deleteKeys);
						if (updatedObj != null) {
							if (updatedObj.getChangedOnServer()) rv.setChangedOnServer(true);
							subIterEntriesToAdd.add(updatedObj);
						}
					}//for each subObj
				}//for each subIter
				rv.addSubIterEntries(subIterAddLists);
			}//if any subiters exist
		}//neither list is null

		if (clientObj != null && rv != null) rv.copyEphemeralFromClient(clientObj);
		return rv;
	}//compareAndUpdate

	
	
	/**
	 * @return the log
	 */
	protected static Logger getLog() {
		return log;
	}
}//TimeStampedNode
