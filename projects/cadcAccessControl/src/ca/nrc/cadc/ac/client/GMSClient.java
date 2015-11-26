/*
 ************************************************************************
 *******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
 **************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
 *
 *  (c) 2014.                            (c) 2014.
 *  Government of Canada                 Gouvernement du Canada
 *  National Research Council            Conseil national de recherches
 *  Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
 *  All rights reserved                  Tous droits réservés
 *
 *  NRC disclaims any warranties,        Le CNRC dénie toute garantie
 *  expressed, implied, or               énoncée, implicite ou légale,
 *  statutory, of any kind with          de quelque nature que ce
 *  respect to the software,             soit, concernant le logiciel,
 *  including without limitation         y compris sans restriction
 *  any warranty of merchantability      toute garantie de valeur
 *  or fitness for a particular          marchande ou de pertinence
 *  purpose. NRC shall not be            pour un usage particulier.
 *  liable in any event for any          Le CNRC ne pourra en aucun cas
 *  damages, whether direct or           être tenu responsable de tout
 *  indirect, special or general,        dommage, direct ou indirect,
 *  consequential or incidental,         particulier ou général,
 *  arising from the use of the          accessoire ou fortuit, résultant
 *  software.  Neither the name          de l'utilisation du logiciel. Ni
 *  of the National Research             le nom du Conseil National de
 *  Council of Canada nor the            Recherches du Canada ni les noms
 *  names of its contributors may        de ses  participants ne peuvent
 *  be used to endorse or promote        être utilisés pour approuver ou
 *  products derived from this           promouvoir les produits dérivés
 *  software without specific prior      de ce logiciel sans autorisation
 *  written permission.                  préalable et particulière
 *                                       par écrit.
 *
 *  This file is part of the             Ce fichier fait partie du projet
 *  OpenCADC project.                    OpenCADC.
 *
 *  OpenCADC is free software:           OpenCADC est un logiciel libre ;
 *  you can redistribute it and/or       vous pouvez le redistribuer ou le
 *  modify it under the terms of         modifier suivant les termes de
 *  the GNU Affero General Public        la “GNU Affero General Public
 *  License as published by the          License” telle que publiée
 *  Free Software Foundation,            par la Free Software Foundation
 *  either version 3 of the              : soit la version 3 de cette
 *  License, or (at your option)         licence, soit (à votre gré)
 *  any later version.                   toute version ultérieure.
 *
 *  OpenCADC is distributed in the       OpenCADC est distribué
 *  hope that it will be useful,         dans l’espoir qu’il vous
 *  but WITHOUT ANY WARRANTY;            sera utile, mais SANS AUCUNE
 *  without even the implied             GARANTIE : sans même la garantie
 *  warranty of MERCHANTABILITY          implicite de COMMERCIALISABILITÉ
 *  or FITNESS FOR A PARTICULAR          ni d’ADÉQUATION À UN OBJECTIF
 *  PURPOSE.  See the GNU Affero         PARTICULIER. Consultez la Licence
 *  General Public License for           Générale Publique GNU Affero
 *  more details.                        pour plus de détails.
 *
 *  You should have received             Vous devriez avoir reçu une
 *  a copy of the GNU Affero             copie de la Licence Générale
 *  General Public License along         Publique GNU Affero avec
 *  with OpenCADC.  If not, see          OpenCADC ; si ce n’est
 *  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
 *                                       <http://www.gnu.org/licenses/>.
 *
 *  $Revision: 4 $
 *
 ************************************************************************
 */
package ca.nrc.cadc.ac.client;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.security.AccessControlContext;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import javax.security.auth.Subject;

import org.apache.log4j.Logger;

import ca.nrc.cadc.ac.Group;
import ca.nrc.cadc.ac.GroupAlreadyExistsException;
import ca.nrc.cadc.ac.GroupNotFoundException;
import ca.nrc.cadc.ac.Role;
import ca.nrc.cadc.ac.User;
import ca.nrc.cadc.ac.UserNotFoundException;
import ca.nrc.cadc.ac.xml.GroupListReader;
import ca.nrc.cadc.ac.xml.GroupReader;
import ca.nrc.cadc.ac.xml.GroupWriter;
import ca.nrc.cadc.auth.AuthenticationUtil;
import ca.nrc.cadc.auth.HttpPrincipal;
import ca.nrc.cadc.auth.SSLUtil;
import ca.nrc.cadc.net.HttpDownload;
import ca.nrc.cadc.net.HttpPost;
import ca.nrc.cadc.net.HttpTransfer;
import ca.nrc.cadc.net.HttpUpload;
import ca.nrc.cadc.net.InputStreamWrapper;
import ca.nrc.cadc.net.NetUtil;
import ca.nrc.cadc.net.event.TransferEvent;
import ca.nrc.cadc.net.event.TransferListener;
import ca.nrc.cadc.reg.client.RegistryClient;


/**
 * Client class for performing group searching and group actions
 * with the access control web service.
 */
public class GMSClient implements TransferListener
{
    private static final Logger log = Logger.getLogger(GMSClient.class);

    // socket factory to use when connecting
    private SSLSocketFactory sslSocketFactory;
    private SSLSocketFactory mySocketFactory;

    // client needs to know which servcie it is bound to and lookup
    // endpoints using RegistryClient
    private URI serviceURI;
    
    // storing baseURL is now considered bad form but fix is out of scope right now
    private String baseURL;


    /**
     * Slightly more complete constructor.  Tests can override the
     * RegistryClient.
     *
     * @param serviceURI            The service URI.
     * @param registryClient        The Registry Client.
     */
    public GMSClient(URI serviceURI, RegistryClient registryClient)
    {
        try
        {
            URL base = registryClient.getServiceURL(serviceURI, "https");
            if (base == null)
                throw new IllegalArgumentException("service not found with https access: " + serviceURI);
            this.baseURL = base.toExternalForm();

            log.debug("AC Service URI: " + this.baseURL);
        }
        catch(MalformedURLException ex)
        {
            throw new RuntimeException("BUG: failed to construct GMS base URL", ex);
        }
    }

    public GMSClient(URI serviceURI)
    {
        this(serviceURI, new RegistryClient());
    }

    /**
     * Constructor.
     *
     * @param baseURL The URL of the supporting access control web service
     * obtained from the registry.
     * @deprecated
     */
    public GMSClient(String baseURL)
        throws IllegalArgumentException
    {
        if (baseURL == null)
        {
            throw new IllegalArgumentException("baseURL is required");
        }
        try
        {
            new URL(baseURL);
        }
        catch (MalformedURLException e)
        {
            throw new IllegalArgumentException("URL is malformed: " +
                                               e.getMessage());
        }

        if (baseURL.endsWith("/"))
        {
            this.baseURL = baseURL.substring(0, baseURL.length() - 1);
        }
        else
        {
            this.baseURL = baseURL;
        }
    }

    public void transferEvent(TransferEvent te)
    {
        if ( TransferEvent.RETRYING == te.getState() )
            log.debug("retry after request failed, reason: "  + te.getError());
    }

    public String getEventHeader()
    {
        return null; // no custom eventID header
    }


    /**
     * Get a list of groups.
     *
     * @return The list of groups.
     */
    public List<Group> getGroups()
    {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Obtain all of the users as userID - name in JSON format.
     *
     * @return List of HTTP Principal users.
     * @throws IOException Any errors in reading.
     */
    public List<User<? extends Principal>> getDisplayUsers() throws IOException
    {
        final List<User<? extends Principal>> webUsers =
                new ArrayList<User<? extends Principal>>();
        final HttpDownload httpDownload =
                    createDisplayUsersHTTPDownload(webUsers);

        httpDownload.setRequestProperty("Accept", "application/json");
        httpDownload.run();

        final Throwable error = httpDownload.getThrowable();

        if (error != null)
        {
            final String errMessage = error.getMessage();
            final int responseCode = httpDownload.getResponseCode();
            log.debug("getDisplayUsers response " + responseCode + ": "
                      + errMessage);
            if ((responseCode == 401) || (responseCode == 403)
                || (responseCode == -1))
            {
                throw new AccessControlException(errMessage);
            }
            else if (responseCode == 400)
            {
                throw new IllegalArgumentException(errMessage);
            }
            else
            {
                throw new IOException("HttpResponse (" + responseCode + ") - "
                                      + errMessage);
            }
        }

        log.debug("Content-Length: " + httpDownload.getContentLength());
        log.debug("Content-Type: " + httpDownload.getContentType());

        return webUsers;
    }


    /**
     * Create a new HTTPDownload instance.  Testers can override as needed.
     *
     * @param webUsers          The User objects.
     * @return                  HttpDownload instance.  Never null.
     * @throws IOException      Any writing/reading errors.
     */
    HttpDownload createDisplayUsersHTTPDownload(
            final List<User<? extends Principal>> webUsers) throws IOException
    {
        final URL usersListURL = new URL(this.baseURL + "/users");
        return new HttpDownload(usersListURL,
                                new JsonUserListInputStreamWrapper(webUsers));
    }

    /**
     * Create a new group.
     *
     * @param group The group to create
     * @return The newly created group will all the information.
     * @throws GroupAlreadyExistsException If a group with the same name already
     *                                     exists.
     * @throws AccessControlException If unauthorized to perform this operation.
     * @throws UserNotFoundException
     * @throws IOException
     */
    public Group createGroup(Group group)
        throws GroupAlreadyExistsException, AccessControlException,
               UserNotFoundException, IOException
    {
        URL createGroupURL = new URL(this.baseURL + "/groups");
        log.debug("createGroupURL request to " + createGroupURL.toString());

        // reset the state of the cache
        clearCache();

        StringBuilder groupXML = new StringBuilder();
        GroupWriter groupWriter = new GroupWriter();
        groupWriter.write(group, groupXML);
        log.debug("createGroup: " + groupXML);

        byte[] bytes = groupXML.toString().getBytes("UTF-8");
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);

        HttpUpload transfer = new HttpUpload(in, createGroupURL);
        transfer.setSSLSocketFactory(getSSLSocketFactory());

        transfer.run();

        Throwable error = transfer.getThrowable();
        if (error != null)
        {
            log.debug("createGroup throwable", error);
            // transfer returns a -1 code for anonymous uploads.
            if ((transfer.getResponseCode() == -1) ||
                (transfer.getResponseCode() == 401) ||
                (transfer.getResponseCode() == 403))
            {
                throw new AccessControlException(error.getMessage());
            }
            if (transfer.getResponseCode() == 400)
            {
                throw new IllegalArgumentException(error.getMessage());
            }
            if (transfer.getResponseCode() == 409)
            {
                throw new GroupAlreadyExistsException(error.getMessage());
            }
            if (transfer.getResponseCode() == 404)
            {
                throw new UserNotFoundException(error.getMessage());
            }
            throw new IOException(error);
        }

        String retXML = transfer.getResponseBody();
        try
        {
            log.debug("createGroup returned: " + retXML);
            GroupReader groupReader = new GroupReader();
            return groupReader.read(retXML);
        }
        catch (Exception bug)
        {
            log.error("Unexpected exception", bug);
            throw new RuntimeException(bug);
        }
    }

    /**
     * Get the group object.
     *
     * @param groupName Identifies the group to get.
     * @return The group.
     * @throws GroupNotFoundException If the group was not found.
     * @throws AccessControlException If unauthorized to perform this operation.
     * @throws java.io.IOException
     */
    public Group getGroup(String groupName)
        throws GroupNotFoundException, AccessControlException, IOException
    {
        URL getGroupURL = new URL(this.baseURL + "/groups/" + groupName);
        log.debug("getGroup request to " + getGroupURL.toString());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HttpDownload transfer = new HttpDownload(getGroupURL, out);

        transfer.setSSLSocketFactory(getSSLSocketFactory());
        transfer.run();

        Throwable error = transfer.getThrowable();
        if (error != null)
        {
            log.debug("getGroup throwable (" + transfer.getResponseCode() + ")", error);
            // transfer returns a -1 code for anonymous access.
            if ((transfer.getResponseCode() == -1) ||
                (transfer.getResponseCode() == 401) ||
                (transfer.getResponseCode() == 403))
            {
                throw new AccessControlException(error.getMessage());
            }
            if (transfer.getResponseCode() == 400)
            {
                throw new IllegalArgumentException(error.getMessage());
            }
            if (transfer.getResponseCode() == 404)
            {
                throw new GroupNotFoundException(error.getMessage());
            }
            throw new IOException(error);
        }

        try
        {
            String groupXML = new String(out.toByteArray(), "UTF-8");
            log.debug("getGroup returned: " + groupXML);
            GroupReader groupReader = new GroupReader();
            return groupReader.read(groupXML);
        }
        catch (Exception bug)
        {
            log.error("Unexpected exception", bug);
            throw new RuntimeException(bug);
        }
    }

    /**
     * Get the all group names.
     *
     * @return The list of names.
     * @throws AccessControlException If unauthorized to perform this operation.
     * @throws java.io.IOException
     */
    public List<String> getGroupNames()
        throws AccessControlException, IOException
    {
        final URL getGroupNamesURL = new URL(this.baseURL + "/groups");
        log.debug("getGroupNames request to " + getGroupNamesURL.toString());

        final List<String> groupNames = new ArrayList<String>();
        final HttpDownload httpDownload =
                new HttpDownload(getGroupNamesURL, new InputStreamWrapper()
        {
            @Override
            public void read(final InputStream inputStream) throws IOException
            {
                try
                {
                    InputStreamReader inReader = new InputStreamReader(inputStream);
                    BufferedReader reader = new BufferedReader(inReader);
                    String line;
                    while ((line = reader.readLine()) != null) {
                        groupNames.add(line);
                    }
                }
                catch (Exception bug)
                {
                    log.error("Unexpected exception", bug);
                    throw new RuntimeException(bug);
                }
            }
        });

        // Disable retries.
        httpDownload.setRetry(0, 0, HttpTransfer.RetryReason.NONE);

        httpDownload.setSSLSocketFactory(getSSLSocketFactory());
        httpDownload.run();

        final Throwable error = httpDownload.getThrowable();

        if (error != null)
        {
            final String errMessage = error.getMessage();
            final int responseCode = httpDownload.getResponseCode();

            log.debug("getGroupNames response " + responseCode + ": " +
                      errMessage);

            if ((responseCode == 401) || (responseCode == 403) ||
                    (responseCode == -1))
            {
                throw new AccessControlException(errMessage);
            }
            if (responseCode == 400)
            {
                throw new IllegalArgumentException(errMessage);
            }
            throw new IOException("HttpResponse (" + responseCode + ") - " + errMessage);
        }

        log.debug("Content-Length: " + httpDownload.getContentLength());
        log.debug("Content-Type: " + httpDownload.getContentType());

        return groupNames;
    }

    /**
     * Update a group.
     *
     * @param group The update group object.
     * @return The group after update.
     * @throws IllegalArgumentException If cyclical membership is detected.
     * @throws GroupNotFoundException If the group was not found.
     * @throws UserNotFoundException If a member was not found.
     * @throws AccessControlException If unauthorized to perform this operation.
     * @throws java.io.IOException
     */
    public Group updateGroup(Group group)
        throws IllegalArgumentException, GroupNotFoundException, UserNotFoundException,
               AccessControlException, IOException
    {
        URL updateGroupURL = new URL(this.baseURL + "/groups/" + group.getID());
        log.debug("updateGroup request to " + updateGroupURL.toString());

        // reset the state of the cache
        clearCache();

        StringBuilder groupXML = new StringBuilder();
        GroupWriter groupWriter = new GroupWriter();
        groupWriter.write(group, groupXML);
        log.debug("updateGroup: " + groupXML);

        HttpPost transfer = new HttpPost(updateGroupURL, groupXML.toString(),
                                         "application/xml", true);
        transfer.setSSLSocketFactory(getSSLSocketFactory());
        transfer.setTransferListener(this);
        transfer.run();


        Throwable error = transfer.getThrowable();
        if (error != null)
        {
            // transfer returns a -1 code for anonymous access.
            if ((transfer.getResponseCode() == -1) ||
                (transfer.getResponseCode() == 401) ||
                (transfer.getResponseCode() == 403))
            {
                throw new AccessControlException(error.getMessage());
            }
            if (transfer.getResponseCode() == 400)
            {
                throw new IllegalArgumentException(error.getMessage());
            }
            if (transfer.getResponseCode() == 404)
            {
                if (error.getMessage() != null && error.getMessage().toLowerCase().contains("user"))
                    throw new UserNotFoundException(error.getMessage());
                else
                    throw new GroupNotFoundException(error.getMessage());
            }
            throw new IOException(error);
        }

        try
        {
            String retXML = transfer.getResponseBody();
            log.debug("getGroup returned: " + retXML);
            GroupReader groupReader = new GroupReader();
            return groupReader.read(retXML);
        }
        catch (Exception bug)
        {
            log.error("Unexpected exception", bug);
            throw new RuntimeException(bug);
        }
    }

    /**
     * Delete the group.
     *
     * @param groupName Identifies the group to delete.
     * @throws GroupNotFoundException If the group was not found.
     * @throws AccessControlException If unauthorized to perform this operation.
     * @throws java.io.IOException
     */
    public void deleteGroup(String groupName)
        throws GroupNotFoundException, AccessControlException, IOException
    {
        URL deleteGroupURL = new URL(this.baseURL + "/groups/" + groupName);
        log.debug("deleteGroup request to " + deleteGroupURL.toString());

        // reset the state of the cache
        clearCache();

        HttpURLConnection conn =
                (HttpURLConnection) deleteGroupURL.openConnection();
        conn.setRequestMethod("DELETE");

        SSLSocketFactory sf = getSSLSocketFactory();
        if ((sf != null) && ((conn instanceof HttpsURLConnection)))
        {
            ((HttpsURLConnection) conn)
                    .setSSLSocketFactory(sf);
        }

        final int responseCode;

        try
        {
            responseCode = conn.getResponseCode();
        }
        catch(Exception e)
        {
            throw new AccessControlException(e.getMessage());
        }

        if (responseCode != 200)
        {
            String errMessage = NetUtil.getErrorBody(conn);
            log.debug("deleteGroup response " + responseCode + ": " +
                      errMessage);

            if ((responseCode == 401) || (responseCode == 403) ||
                    (responseCode == -1))
            {
                throw new AccessControlException(errMessage);
            }
            if (responseCode == 400)
            {
                throw new IllegalArgumentException(errMessage);
            }
            if (responseCode == 404)
            {
                throw new GroupNotFoundException(errMessage);
            }
            throw new IOException("HttpResponse (" + responseCode + ") - " + errMessage);
        }
    }

    /**
     * Add a group as a member of another group.
     *
     * @param targetGroupName The group in which to add the group member.
     * @param groupMemberName The group member to add.
     * @throws IllegalArgumentException If cyclical membership is detected.
     * @throws GroupNotFoundException If the group was not found.
     * @throws AccessControlException If unauthorized to perform this operation.
     * @throws java.io.IOException
     */
    public void addGroupMember(String targetGroupName, String groupMemberName)
        throws IllegalArgumentException, GroupNotFoundException,
               AccessControlException, IOException
    {
        URL addGroupMemberURL = new URL(this.baseURL + "/groups/" +
                                        targetGroupName + "/groupMembers/" +
                                        groupMemberName);
        log.debug("addGroupMember request to " + addGroupMemberURL.toString());

        // reset the state of the cache
        clearCache();

        final InputStream is = new ByteArrayInputStream(new byte[0]);
        final HttpUpload httpUpload = new HttpUpload(is, addGroupMemberURL);

        httpUpload.setSSLSocketFactory(getSSLSocketFactory());
        httpUpload.run();

        final Throwable error = httpUpload.getThrowable();
        if (error != null)
        {
            final int responseCode = httpUpload.getResponseCode();
            final String errMessage = error.getMessage();

            if ((responseCode == -1) ||
                (responseCode == 401) ||
                (responseCode == 403))
            {
                throw new AccessControlException(errMessage);
            }
            if (responseCode == 400)
            {
                throw new IllegalArgumentException(errMessage);
            }
            if (responseCode == 404)
            {
                throw new GroupNotFoundException(errMessage);
            }
            throw new IOException(errMessage);
        }
    }

    /**
     * Add a user as a member of a group.
     *
     * @param targetGroupName The group in which to add the group member.
     * @param userID The user to add.
     * @throws GroupNotFoundException If the group was not found.
     * @throws UserNotFoundException If the member was not found.
     * @throws java.io.IOException
     * @throws AccessControlException If unauthorized to perform this operation.
     */
    public void addUserMember(String targetGroupName, Principal userID)
        throws GroupNotFoundException, UserNotFoundException, AccessControlException, IOException
    {
        log.debug("addUserMember: " + targetGroupName + " + " + userID.getName());

        String userIDType = AuthenticationUtil.getPrincipalType(userID);
        URL addUserMemberURL = new URL(this.baseURL + "/groups/" + targetGroupName
                + "/userMembers/" + NetUtil.encode(userID.getName())
                + "?idType=" + userIDType);

        log.debug("addUserMember request to " + addUserMemberURL.toString());

        // reset the state of the cache
        clearCache();

        final InputStream is = new ByteArrayInputStream(new byte[0]);
        final HttpUpload httpUpload = new HttpUpload(is, addUserMemberURL);

        httpUpload.setSSLSocketFactory(getSSLSocketFactory());
        httpUpload.run();

        final Throwable error = httpUpload.getThrowable();
        if (error != null)
        {
            final int responseCode = httpUpload.getResponseCode();
            final String errMessage = error.getMessage();

            if ((responseCode == -1) ||
                (responseCode == 401) ||
                (responseCode == 403))
            {
                throw new AccessControlException(errMessage);
            }
            if (responseCode == 400)
            {
                throw new IllegalArgumentException(errMessage);
            }
            if (responseCode == 404)
            {
                if (errMessage != null && errMessage.toLowerCase().contains("user"))
                    throw new UserNotFoundException(errMessage);
                else
                    throw new GroupNotFoundException(errMessage);
            }
            throw new IOException(errMessage);
        }
    }

    /**
     * Remove a group as a member of another group.
     *
     * @param targetGroupName The group from which to remove the group member.
     * @param groupMemberName The group member to remove.
     * @throws GroupNotFoundException If the group was not found.
     * @throws java.io.IOException
     * @throws AccessControlException If unauthorized to perform this operation.
     */
    public void removeGroupMember(String targetGroupName,
                                  String groupMemberName)
        throws GroupNotFoundException, AccessControlException, IOException
    {
        URL removeGroupMemberURL = new URL(this.baseURL + "/groups/" +
                                           targetGroupName + "/groupMembers/" +
                                           groupMemberName);
        log.debug("removeGroupMember request to " +
                  removeGroupMemberURL.toString());

        // reset the state of the cache
        clearCache();

        HttpURLConnection conn =
                (HttpURLConnection) removeGroupMemberURL.openConnection();
        conn.setRequestMethod("DELETE");

        SSLSocketFactory sf = getSSLSocketFactory();
        if ((sf != null) && ((conn instanceof HttpsURLConnection)))
        {
            ((HttpsURLConnection) conn)
                    .setSSLSocketFactory(getSSLSocketFactory());
        }

        // Try to handle anonymous access and throw AccessControlException
        int responseCode = -1;
        try
        {
            responseCode = conn.getResponseCode();
        }
        catch (Exception ignore) {}

        if (responseCode != 200)
        {
            String errMessage = NetUtil.getErrorBody(conn);
            log.debug("removeGroupMember response " + responseCode + ": " +
                      errMessage);

            if ((responseCode == -1) ||
                (responseCode == 401) ||
                (responseCode == 403))
            {
                throw new AccessControlException(errMessage);
            }
            if (responseCode == 400)
            {
                throw new IllegalArgumentException(errMessage);
            }
            if (responseCode == 404)
            {
                throw new GroupNotFoundException(errMessage);
            }
            throw new IOException(errMessage);
        }
    }

    /**
     * Remove a user as a member of a group.
     *
     * @param targetGroupName The group from which to remove the group member.
     * @param userID The user to remove.
     * @throws GroupNotFoundException If the group was not found.
     * @throws UserNotFoundException If the member was not found.
     * @throws java.io.IOException
     * @throws AccessControlException If unauthorized to perform this operation.
     */
    public void removeUserMember(String targetGroupName, Principal userID)
        throws GroupNotFoundException, UserNotFoundException, AccessControlException, IOException
    {
        String userIDType = AuthenticationUtil.getPrincipalType(userID);

        log.debug("removeUserMember: " + targetGroupName + " - " + userID.getName() + " type: " + userIDType);

        URL removeUserMemberURL = new URL(this.baseURL + "/groups/" + targetGroupName
                + "/userMembers/" + NetUtil.encode(userID.getName())
                + "?idType=" + userIDType);

        log.debug("removeUserMember: " + removeUserMemberURL.toString());

        // reset the state of the cache
        clearCache();

        HttpURLConnection conn =
                (HttpURLConnection) removeUserMemberURL.openConnection();
        conn.setRequestMethod("DELETE");

        SSLSocketFactory sf = getSSLSocketFactory();
        if ((sf != null) && ((conn instanceof HttpsURLConnection)))
        {
            ((HttpsURLConnection) conn)
                    .setSSLSocketFactory(getSSLSocketFactory());
        }

        // Try to handle anonymous access and throw AccessControlException
        int responseCode = -1;
        try
        {
            responseCode = conn.getResponseCode();
        }
        catch (Exception ignore) {}

        if (responseCode != 200)
        {
            String errMessage = NetUtil.getErrorBody(conn);
            log.debug("removeUserMember response " + responseCode + ": " +
                      errMessage);

            if ((responseCode == -1) ||
                (responseCode == 401) ||
                (responseCode == 403))
            {
                throw new AccessControlException(errMessage);
            }
            if (responseCode == 400)
            {
                throw new IllegalArgumentException(errMessage);
            }
            if (responseCode == 404)
            {
                if (errMessage != null && errMessage.toLowerCase().contains("user"))
                    throw new UserNotFoundException(errMessage);
                else
                    throw new GroupNotFoundException(errMessage);
            }
            throw new IOException(errMessage);
        }
    }

    private Principal getCurrentUserID()
    {
        Subject cur = AuthenticationUtil.getCurrentSubject();
        if (cur == null)
            throw new IllegalArgumentException("no subject");
        Set<HttpPrincipal> ps = cur.getPrincipals(HttpPrincipal.class); // hack
        if (ps.isEmpty())
            throw new IllegalArgumentException("no principals");
        Principal p = ps.iterator().next();
        log.debug("getCurrentID: " + p.getClass());
        return p;
    }

    /**
     * Get memberships for the current user (subject).
     *
     * @param role
     * @return A list of groups for which the current user has the role.
     * @throws AccessControlException
     * @throws ca.nrc.cadc.ac.UserNotFoundException
     * @throws java.io.IOException
     */
    public List<Group> getMemberships(Role role)
        throws UserNotFoundException, AccessControlException, IOException
    {
        return getMemberships(getCurrentUserID(), role);
    }

    /**
     * Get all the memberships of the user of a certain role.
     *
     * @param userID Identifies the user.
     * @param role The role to look up.
     * @return A list of groups for which the user has the role.
     * @throws UserNotFoundException If the user does not exist.
     * @throws AccessControlException If not allowed to perform the search.
     * @throws IllegalArgumentException If a parameter is null.
     * @throws IOException If an unknown error occurred.
     */
    public List<Group> getMemberships(Principal userID, Role role)
        throws UserNotFoundException, AccessControlException, IOException
    {
        if (userID == null || role == null)
        {
            throw new IllegalArgumentException("userID and role are required.");
        }

        List<Group> cachedGroups = getCachedGroups(userID, role, true);
        if (cachedGroups != null)
        {
            return cachedGroups;
        }

        String idType = AuthenticationUtil.getPrincipalType(userID);
        String id = userID.getName();
        String roleString = role.getValue();

        StringBuilder searchGroupURL = new StringBuilder(this.baseURL);
        searchGroupURL.append("/search?");

        searchGroupURL.append("ID=").append(NetUtil.encode(id));
        searchGroupURL.append("&IDTYPE=").append(NetUtil.encode(idType));
        searchGroupURL.append("&ROLE=").append(NetUtil.encode(roleString));

        log.debug("getMemberships request to " + searchGroupURL.toString());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        URL url = new URL(searchGroupURL.toString());
        HttpDownload transfer = new HttpDownload(url, out);

        transfer.setSSLSocketFactory(getSSLSocketFactory());
        transfer.run();

        Throwable error = transfer.getThrowable();
        if (error != null)
        {
            log.debug("getMemberships throwable", error);
            // transfer returns a -1 code for anonymous access.
            if ((transfer.getResponseCode() == -1) ||
                (transfer.getResponseCode() == 401) ||
                (transfer.getResponseCode() == 403))
            {
                throw new AccessControlException(error.getMessage());
            }
            if (transfer.getResponseCode() == 404)
            {
                throw new UserNotFoundException(error.getMessage());
            }
            if (transfer.getResponseCode() == 400)
            {
                throw new IllegalArgumentException(error.getMessage());
            }
            throw new IOException(error);
        }

        try
        {
            String groupsXML = new String(out.toByteArray(), "UTF-8");
            log.debug("getMemberships returned: " + groupsXML);
            GroupListReader groupListReader = new GroupListReader();
            List<Group> groups = groupListReader.read(groupsXML);
            setCachedGroups(userID, groups, role);
            return groups;
        }
        catch (Exception bug)
        {
            log.error("Unexpected exception", bug);
            throw new RuntimeException(bug);
        }
    }

    /**
     * Return the group, specified by paramter groupName, if the user,
     * identified by userID, is a member of that group.  Return null
     * otherwise.
     *
     * This call is identical to getMemberShip(userID, groupName, Role.MEMBER)
     *
     * @param userID Identifies the user.
     * @param groupName Identifies the group.
     * @return The group or null of the user is not a member.
     * @throws UserNotFoundException If the user does not exist.
     * @throws AccessControlException If not allowed to peform the search.
     * @throws IllegalArgumentException If a parameter is null.
     * @throws IOException If an unknown error occured.
     */
    public Group getMembership(Principal userID, String groupName)
        throws UserNotFoundException, AccessControlException, IOException
    {
        return getMembership(userID, groupName, Role.MEMBER);
    }

    /**
     * Return the group, specified by paramter groupName, if the user,
     * identified by userID, is a member (of type role) of that group.
     * Return null otherwise.
     *
     * @param userID Identifies the user.
     * @param groupName Identifies the group.
     * @param role The membership role to search.
     * @return The group or null of the user is not a member.
     * @throws UserNotFoundException If the user does not exist.
     * @throws AccessControlException If not allowed to peform the search.
     * @throws IllegalArgumentException If a parameter is null.
     * @throws IOException If an unknown error occured.
     */
    public Group getMembership(Principal userID, String groupName, Role role)
        throws UserNotFoundException, AccessControlException, IOException
    {
        if (userID == null || groupName == null || role == null)
        {
            throw new IllegalArgumentException("userID and role are required.");
        }

        Group cachedGroup = getCachedGroup(userID, groupName, role);
        if (cachedGroup != null)
        {
            return cachedGroup;
        }

        String idType = AuthenticationUtil.getPrincipalType(userID);
        String id = userID.getName();
        String roleString = role.getValue();

        StringBuilder searchGroupURL = new StringBuilder(this.baseURL);
        searchGroupURL.append("/search?");

        searchGroupURL.append("ID=").append(NetUtil.encode(id));
        searchGroupURL.append("&IDTYPE=").append(NetUtil.encode(idType));
        searchGroupURL.append("&ROLE=").append(NetUtil.encode(roleString));
        searchGroupURL.append("&GROUPID=").append(NetUtil.encode(groupName));

        log.debug("getMembership request to " + searchGroupURL.toString());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        URL url = new URL(searchGroupURL.toString());
        HttpDownload transfer = new HttpDownload(url, out);

        transfer.setSSLSocketFactory(getSSLSocketFactory());
        transfer.run();

        Throwable error = transfer.getThrowable();
        if (error != null)
        {
            log.debug("getMembership throwable", error);
            // transfer returns a -1 code for anonymous access.
            if ((transfer.getResponseCode() == -1) ||
                (transfer.getResponseCode() == 401) ||
                (transfer.getResponseCode() == 403))
            {
                throw new AccessControlException(error.getMessage());
            }
            if (transfer.getResponseCode() == 404)
            {
                throw new UserNotFoundException(error.getMessage());
            }
            if (transfer.getResponseCode() == 400)
            {
                throw new IllegalArgumentException(error.getMessage());
            }
            throw new IOException(error);
        }

        try
        {
            String groupsXML = new String(out.toByteArray(), "UTF-8");
            log.debug("getMembership returned: " + groupsXML);
            GroupListReader groupListReader = new GroupListReader();
            List<Group> groups = groupListReader.read(groupsXML);
            if (groups.size() == 0)
            {
                return null;
            }
            if (groups.size() == 1)
            {
                Group ret = groups.get(0);
                addCachedGroup(userID, ret, role);
                return ret;
            }
            throw new IllegalStateException(
                    "Duplicate membership for " + id + " in group " + groupName);
        }
        catch (Exception bug)
        {
            log.error("Unexpected exception", bug);
            throw new RuntimeException(bug);
        }
    }

    /**
     * Check group membership of the current Subject.
     *
     * @param groupName
     * @return
     * @throws UserNotFoundException
     * @throws AccessControlException
     * @throws IOException
     */
    public boolean isMember(String groupName)
        throws UserNotFoundException, AccessControlException, IOException
    {
        return isMember(getCurrentUserID(), groupName, Role.MEMBER);
    }

    /**
     * Check if userID is a member of groupName.
     *
     * This is equivalent to isMember(userID, groupName, Role.MEMBER)
     *
     * @param userID Identifies the user.
     * @param groupName Identifies the group.
     * @return True if the user is a member of the group
     * @throws UserNotFoundException If the user does not exist.
     * @throws AccessControlException If not allowed to peform the search.
     * @throws IllegalArgumentException If a parameter is null.
     * @throws IOException If an unknown error occured.
     */
    public boolean isMember(Principal userID, String groupName)
        throws UserNotFoundException, AccessControlException, IOException
    {
        return isMember(userID, groupName, Role.MEMBER);
    }

    /**
     * Check if userID is a member (of type role) of groupName.
     *
     * @param userID Identifies the user.
     * @param groupName Identifies the group.
     * @param role The type of membership.
     * @return True if the user is a member of the group
     * @throws UserNotFoundException If the user does not exist.
     * @throws AccessControlException If not allowed to peform the search.
     * @throws IllegalArgumentException If a parameter is null.
     * @throws IOException If an unknown error occured.
     */
    public boolean isMember(Principal userID, String groupName, Role role)
        throws UserNotFoundException, AccessControlException, IOException
    {
        Group group = getMembership(userID, groupName, role);
        return group != null;
    }

    /**
     * @param sslSocketFactory the sslSocketFactory to set
     */
    public void setSSLSocketFactory(SSLSocketFactory sslSocketFactory)
    {
        if (mySocketFactory != null)
            throw new IllegalStateException("Illegal use of GMSClient: "
                    + "cannot set SSLSocketFactory after using one created from Subject");
        this.sslSocketFactory = sslSocketFactory;
        clearCache();
    }

    private int subjectHashCode = 0;
    private SSLSocketFactory getSSLSocketFactory()
    {
        AccessControlContext ac = AccessController.getContext();
        Subject s = Subject.getSubject(ac);

        // no real Subject: can only use the one from setSSLSocketFactory
        if (s == null || s.getPrincipals().isEmpty())
        {
            return sslSocketFactory;
        }

        // lazy init
        if (this.mySocketFactory == null)
        {
            log.debug("getSSLSocketFactory: " + s);
            this.mySocketFactory = SSLUtil.getSocketFactory(s);
            this.subjectHashCode = s.hashCode();
        }
        else
        {
            int c = s.hashCode();
            if (c != subjectHashCode)
                throw new IllegalStateException("Illegal use of "
                        + this.getClass().getSimpleName()
                        + ": subject change not supported for internal SSLSocketFactory");
        }
        return this.mySocketFactory;
    }

    protected void clearCache()
    {
        AccessControlContext acContext = AccessController.getContext();
        Subject subject = Subject.getSubject(acContext);
        if (subject != null)
        {
            subject.getPrivateCredentials().remove(new GroupMemberships());
        }
    }

    protected GroupMemberships getGroupCache(Principal userID)
    {
        AccessControlContext acContext = AccessController.getContext();
        Subject subject = Subject.getSubject(acContext);

        // only consult cache if the userID is of the calling subject
        if (userIsSubject(userID, subject))
        {
            Set<GroupMemberships> gset = subject.getPrivateCredentials(GroupMemberships.class);
            if (gset == null || gset.isEmpty())
            {
                GroupMemberships mems = new GroupMemberships();
                subject.getPrivateCredentials().add(mems);
                return mems;
            }
            GroupMemberships mems = gset.iterator().next();
            return mems;
        }
        return null; // no cache
    }

    protected Group getCachedGroup(Principal userID, String groupID, Role role)
    {
        List<Group> groups = getCachedGroups(userID, role, false);
        if (groups == null)
            return null; // no cache
        for (Group g : groups)
        {
            if (g.getID().equals(groupID))
                return g;
        }
        return null;
    }
    protected List<Group> getCachedGroups(Principal userID, Role role, boolean complete)
    {
        GroupMemberships mems = getGroupCache(userID);
        if (mems == null)
            return null; // no cache

        Boolean cacheState = mems.complete.get(role);
        if (!complete || Boolean.TRUE.equals(cacheState))
            return mems.memberships.get(role);

        // caller wanted complete and we don't have that
        return null;
    }

    protected void addCachedGroup(Principal userID, Group group, Role role)
    {
        GroupMemberships mems = getGroupCache(userID);
        if (mems == null)
            return; // no cache

        List<Group> groups = mems.memberships.get(role);
        if (groups == null)
        {
            groups = new ArrayList<Group>();
            mems.complete.put(role, Boolean.FALSE);
            mems.memberships.put(role, groups);
        }
        if (!groups.contains(group))
            groups.add(group);
    }

    protected void setCachedGroups(Principal userID, List<Group> groups, Role role)
    {
        GroupMemberships mems = getGroupCache(userID);
        if (mems == null)
            return; // no cache

        log.debug("Caching groups for " + userID + ", role " + role);
        List<Group> cur = mems.memberships.get(role);
        if (cur == null)
        {
            cur = new ArrayList<Group>();
            mems.complete.put(role, Boolean.FALSE);
            mems.memberships.put(role, cur);
        }
        for (Group group : groups)
        {
            if (!cur.contains(group))
                cur.add(group);
            mems.complete.put(role, Boolean.TRUE);
        }
    }

    protected boolean userIsSubject(Principal userID, Subject subject)
    {
        if (userID == null || subject == null)
        {
            return false;
        }

        for (Principal subjectPrincipal : subject.getPrincipals())
        {
            if (AuthenticationUtil.equals(subjectPrincipal, userID))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Class used to hold list of groups in which a user is known to be a member.
     */
    protected class GroupMemberships implements Comparable
    {
        Map<Role, List<Group>> memberships = new HashMap<Role, List<Group>>();
        Map<Role, Boolean> complete = new HashMap<Role, Boolean>();

        protected GroupMemberships()
        {
        }

        // only allow one in a set - makes clearCache simple too
        public boolean equals(Object rhs)
        {
            if (rhs != null && rhs instanceof GroupMemberships)
                return true;
            return false;
        }

        public int compareTo(Object t)
        {
            if (this.equals(t))
                return 0;
            return -1; // wonder if this is sketchy
        }
    }

}
