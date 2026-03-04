package org.jivesoftware.openfire.plugin.rest.controller;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.pep.PEPService;
import org.jivesoftware.openfire.pep.PEPServiceInfo;
import org.jivesoftware.openfire.pep.PEPServiceManager;
import org.jivesoftware.openfire.plugin.rest.entity.pubsub.PubSubNodeEntities;
import org.jivesoftware.openfire.plugin.rest.entity.pubsub.PubSubNodeEntity;
import org.jivesoftware.openfire.plugin.rest.entity.pubsub.PubSubNodePublishedItemEntity;
import org.jivesoftware.openfire.plugin.rest.exceptions.ExceptionType;
import org.jivesoftware.openfire.plugin.rest.exceptions.ServiceException;
import org.jivesoftware.openfire.pubsub.Node;
import org.jivesoftware.openfire.pubsub.PubSubServiceInfo;
import org.jivesoftware.openfire.pubsub.PublishedItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
public class PubSubController {
    /** The Constant INSTANCE. */
    public static final PubSubController INSTANCE = new PubSubController();

    /** The pep service manager. */
    private PEPServiceManager pepServiceManager;

    /** The xmpp server. */
    private XMPPServer xmppServer;

    /** The log. */
    private static final Logger LOG = LoggerFactory.getLogger(PubSubController.class);

    /**
     * Instantiates a new pub sub controller.
     */
    private PubSubController() {
        xmppServer = XMPPServer.getInstance();
        pepServiceManager = new PEPServiceManager();
    }

    /**
     * Gets the single instance of PubSubController.
     *
     * @return single instance of PubSubController
     */
    public static PubSubController getInstance() {
        return INSTANCE;
    }



    /**
     * Retrieves the list of PubSub nodes (leaf nodes) associated with a specific user.
     *
     * @param ownerString the username or JID of the user whose PubSub nodes are to be retrieved.
     *                    Must not be null or invalid.
     * @return a {@link PubSubNodeEntities} object containing the list of leaf PubSub nodes and their count.
     * @throws ServiceException if the username/JID is null, invalid, or if no PEP service or PubSub nodes
     *                          are found for the specified user.
     */
    public PubSubNodeEntities getPubSubNodesOfUser(String ownerString) throws ServiceException {
        LOG.info("Getting leaf nodes for user {}", ownerString);
        if (ownerString == null) {
            LOG.warn("Username cannot be null for user {}", ownerString);
            throw new ServiceException("Username cannot be null", "", ExceptionType.USER_NOT_FOUND_EXCEPTION, Response.Status.BAD_REQUEST, null);
        }
        JID owner = null;
        try {
            if (ownerString.contains("@")) {
                owner = new JID(ownerString).asBareJID();
                LOG.debug("Found bare JID for user {}", owner);
            } else {
                owner = xmppServer.createJID(ownerString, null);
                LOG.debug("Found full JID for user {}", owner);
            }
        } catch (IllegalArgumentException e) {
            LOG.warn("Invalid username/JID provided: {}", ownerString);
            throw new ServiceException("Invalid username or JID", "", ExceptionType.USER_NOT_FOUND_EXCEPTION, Response.Status.BAD_REQUEST, e);
        }

        // Double-check if pepService is null or throw Null Exception
        // Needs Time at Object creation 
        // if PEP Service of "owner" wasn't asked once after restart of openfire
        // if removed may throw a 500 Server Failure at the 1st query of user
        //  START --->
        PEPService pepService = null;
        try{
            pepService = pepServiceManager.getPEPService(owner);
        }catch (NullPointerException e){
            LOG.info("Could not get PEP-Service for User {}; Will try again", owner.toBareJID());
        }
        try{
            if (pepService == null) {
                pepService = pepServiceManager.getPEPService(owner);
            }
        }catch (NullPointerException e1){
            LOG.warn("Failed to get PEP-Service for User {}", owner.toBareJID());
            throw new ServiceException("Failed to get PEP-Service for User ", "", ExceptionType.PROPERTY_NOT_FOUND, Response.Status.ACCEPTED, null);
        }
        if ( pepService == null) {
            LOG.info("No PEP service found for user {}", owner.toBareJID());
            throw new ServiceException("No PEP service found for user", "", ExceptionType.USER_NOT_FOUND_EXCEPTION, Response.Status.NOT_FOUND, null);
        }
        // <--- END

        PubSubServiceInfo pubSubServiceInfo = new PEPServiceInfo( owner );
        List<Node> nodes = pubSubServiceInfo.getLeafNodes();
        if (nodes == null) {
            LOG.info("No leaf nodes found for user {}", owner);
            throw new ServiceException("No leaf nodes found for user", "", ExceptionType.USER_NOT_FOUND_EXCEPTION, Response.Status.NOT_FOUND, null);
        }
        LOG.debug("Found {} leaf nodes for user {}", nodes.size(), owner);
        PubSubNodeEntities pubSubNodeEntities = nodeToPubSubMapper(nodes);
        return pubSubNodeEntities;
    }


    /**
     * Maps a list of {@link Node} objects into a {@link PubSubNodeEntities} object containing
     * their corresponding details and published items.
     *
     * @param nodes the list of {@link Node} objects to be mapped. Must not be null.
     * @return a {@link PubSubNodeEntities} object containing the mapped PubSub nodes
     *         and their details, including published items.
     * @throws ServiceException if there is an error while mapping the nodes or their
     *         associated published items.
     */
    private PubSubNodeEntities nodeToPubSubMapper(List<Node> nodes) throws ServiceException {
        PubSubNodeEntities pubSubNodeEntities = new PubSubNodeEntities();
        List<PubSubNodeEntity> nodeList = new ArrayList<>();
        for (Node node : nodes) {
            List<PubSubNodePublishedItemEntity> nodeItems = new ArrayList<>();
            try {
                for (PublishedItem pubitem : node.getPublishedItems()) {
                    nodeItems.add(
                        new PubSubNodePublishedItemEntity(
                            pubitem.getID(),
                            pubitem.getPublisher().toBareJID(),
                            pubitem.getCreationDate(),
                            pubitem.getPayloadXML()
                        )
                    );
                }
            } catch (Exception e) {
                LOG.error("Error map published items for node: {}", node.getName(), e);
                throw new ServiceException("Error mapping published items for node","", ExceptionType.PROPERTY_NOT_FOUND, Response.Status.INTERNAL_SERVER_ERROR, e);
            }
            try{
                PubSubNodeEntity nodeItem = new PubSubNodeEntity(
                node.getName(),
                node.getDescription(),
                node.getNodeID(),
                node.getAllSubscriptions().size(),
                node.getAllAffiliates().size(),
                node.getCreationDate(),
                node.getModificationDate(),
                nodeItems
                );
                nodeList.add(nodeItem);
            }catch (Exception e){
                LOG.error("Error map single node: {}", node.getName(), e);
                throw new ServiceException("Error mapping node","", ExceptionType.PROPERTY_NOT_FOUND, Response.Status.INTERNAL_SERVER_ERROR, e);
            }
        }
        try {
            pubSubNodeEntities.setNodes(nodeList);
        }catch (Exception e){
            LOG.error("Error map nodes", e);
            throw new ServiceException("Error mapping nodes","", ExceptionType.PROPERTY_NOT_FOUND, Response.Status.INTERNAL_SERVER_ERROR, e);
        }
        return pubSubNodeEntities;
    }


}
