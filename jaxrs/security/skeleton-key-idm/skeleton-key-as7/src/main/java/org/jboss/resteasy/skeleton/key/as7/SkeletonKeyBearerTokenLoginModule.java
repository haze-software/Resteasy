package org.jboss.resteasy.skeleton.key.as7;

import org.apache.catalina.connector.Request;
import org.jboss.resteasy.security.PemUtils;
import org.jboss.resteasy.skeleton.key.RSATokenVerifier;
import org.jboss.resteasy.skeleton.key.ResourceMetadata;
import org.jboss.resteasy.skeleton.key.SkeletonKeyTokenVerification;
import org.jboss.resteasy.skeleton.key.VerificationException;
import org.jboss.security.SimpleGroup;
import org.jboss.security.SimplePrincipal;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.login.LoginException;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PublicKey;
import java.security.acl.Group;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class SkeletonKeyBearerTokenLoginModule extends JBossWebAuthLoginModule
{
   static ConcurrentHashMap<String, ResourceMetadata> resourceMetadataCache = new ConcurrentHashMap<String, ResourceMetadata>();

   protected ResourceMetadata resourceMetadata;
   protected SkeletonKeyTokenVerification verification;
   protected boolean challenge;

   private static KeyStore loadKeyStore(String filename, String password) throws Exception
   {
      KeyStore trustStore = KeyStore.getInstance(KeyStore
              .getDefaultType());
      File truststoreFile = new File(filename);
      FileInputStream trustStream = new FileInputStream(truststoreFile);
      trustStore.load(trustStream, password.toCharArray());
      trustStream.close();
      return trustStore;
   }


   @Override
   public void initialize(Subject subject, CallbackHandler callbackHandler, Map<String, ?> sharedState, Map<String, ?> options)
   {
      super.initialize(subject, callbackHandler, sharedState, options);
      String name = (String) options.get("resource-name");
      if (name == null) throw new RuntimeException("Must set resource-name in security realm config");
      String realm = (String) options.get("realm");
      if (realm == null) throw new RuntimeException(("Must set realm in security realm config"));

      String cacheKey = realm;
      if (name != null) cacheKey += ":" + name;
      resourceMetadata = resourceMetadataCache.get(cacheKey);
      String ch = (String)options.get("challenge");
      if (ch != null) challenge = Boolean.parseBoolean(ch);
      if (resourceMetadata != null) return;

      String realmKeyPem = (String) options.get("realm-public-key");
      if (realmKeyPem == null)
      {
         throw new IllegalArgumentException("You must set the realm-public-key");
      }

      PublicKey realmKey = null;
      try
      {
         realmKey = PemUtils.decodePublicKey(realmKeyPem);
      }
      catch (Exception e)
      {
         throw new RuntimeException(e);
      }
      resourceMetadata = new ResourceMetadata();
      resourceMetadata.setRealm(realm);
      resourceMetadata.setResourceName(name);
      resourceMetadata.setRealmKey(realmKey);



      String truststore = (String) options.get("truststore");
      if (truststore != null)
      {
         String truststorePassword = (String) options.get("truststore-password");
         KeyStore trust = null;
         try
         {
            trust = loadKeyStore(truststore, truststorePassword);
         }
         catch (Exception e)
         {
            throw new RuntimeException(e);
         }
         resourceMetadata.setTruststore(trust);
      }
      String serverKeystore = (String) options.get("resource-keystore");
      if (serverKeystore != null)
      {
         String serverKeystorePassword = (String) options.get("resource-keystore-password");
         KeyStore serverKS = null;
         try
         {
            serverKS = loadKeyStore(serverKeystore, serverKeystorePassword);
         }
         catch (Exception e)
         {
            throw new RuntimeException(e);
         }
         resourceMetadata.setClientKeystore(serverKS);
      }
      resourceMetadataCache.putIfAbsent(cacheKey, resourceMetadata);
   }

   @Override
   protected boolean login(Request request, HttpServletResponse response) throws LoginException
   {
      CatalinaBearerTokenAuthenticator authenticator = new CatalinaBearerTokenAuthenticator(challenge, resourceMetadata);
      loginOk = authenticator.login(request, response);
      verification = authenticator.getVerification();
      return loginOk;
   }

   @Override
   protected Principal getIdentity()
   {
      return verification.getPrincipal();
   }

   @Override
   protected Group[] getRoleSets() throws LoginException
   {
      SimpleGroup roles = new SimpleGroup("Roles");
      Group[] roleSets = {roles};
      for (String role : verification.getRoles())
      {
         roles.addMember(new SimplePrincipal(role));
      }
      return roleSets;
   }

}
