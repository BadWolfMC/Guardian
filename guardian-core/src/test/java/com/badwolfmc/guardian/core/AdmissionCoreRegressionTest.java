package com.badwolfmc.guardian.core;
import com.badwolfmc.guardian.protocol.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
class AdmissionCoreRegressionTest {
 @Test void classifiesOnlyExactNormalizedBrands(){assertEquals(ClientClassification.JAVA_VANILLA,BrandClassifier.classify(" VANILLA "));assertEquals(ClientClassification.JAVA_FABRIC,BrandClassifier.classify("fabric"));assertEquals(ClientClassification.JAVA_OPTIFINE,BrandClassifier.classify("OptiFine"));assertEquals(ClientClassification.JAVA_UNKNOWN,BrandClassifier.classify("fabric-but-not-really"));}
 @Test void supportedBedrockEvidencePrecedesJavaBrand(){assertEquals(ClientClassification.BEDROCK,ClientOriginClassifier.classify(true,false,"fabric"));assertEquals(ClientClassification.BEDROCK,ClientOriginClassifier.classify(false,true,null));}
 @Test void javaRemainsJavaWithoutBedrockEvidence(){assertEquals(ClientClassification.JAVA_FABRIC,ClientOriginClassifier.classify(false,false,"fabric"));assertEquals(ClientClassification.JAVA_UNKNOWN,ClientOriginClassifier.classify(false,false,"Geyser"));assertEquals(ClientClassification.JAVA_UNKNOWN,ClientOriginClassifier.classify(false,false,"Floodgate"));assertEquals(ClientClassification.JAVA_UNKNOWN,ClientOriginClassifier.classify(false,false,"Bedrock"));}
 @Test void proxyAdmissionStillBindsUuidAndFreshness(){java.util.UUID id=java.util.UUID.randomUUID();long now=50_000L;byte[] sid=new byte[GuardianProtocol.PROXY_SESSION_ID_BYTES];ProxyAdmissionAssertion a=new ProxyAdmissionAssertion(GuardianProtocol.PROXY_ASSERTION_VERSION,id,sid,ConnectionOrigin.JAVA,now-1000,now+5000);assertEquals(DecisionReason.PROXY_ADMISSION_VERIFIED,ProxyAdmissionValidator.validate(a,id,now).reason());assertEquals(DecisionReason.PROXY_ASSERTION_INVALID,ProxyAdmissionValidator.validate(a,java.util.UUID.randomUUID(),now).reason());}
}
