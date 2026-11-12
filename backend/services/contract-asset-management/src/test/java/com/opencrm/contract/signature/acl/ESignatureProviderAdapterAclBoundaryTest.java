package com.opencrm.contract.signature.acl;

import com.opencrm.contract.signature.SignatureEnvelopeStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DRG-457 / BC-06-04. Proves the ACL boundary two ways:
 * <ol>
 *   <li>a reflection sweep of {@link ESignatureProviderAdapter} showing every method's parameter and return type is
 *       either a JDK type ({@code String}, {@code java.time}, {@code java.util}) or a type declared in this
 *       {@code acl} package — never a hypothetical provider SDK class, and never an entity or a published event
 *       type from {@code com.opencrm.contract.signature} / {@code com.opencrm.common.event.dto};</li>
 *   <li>instantiating {@link StubESignatureProviderAdapter} against the interface and driving SendForSignature,
 *       GetEnvelopeStatus and VoidEnvelope through it, so the shape is exercised, not just declared.</li>
 * </ol>
 */
@DisplayName("DRG-457: ESignatureProviderAdapter ACL boundary")
class ESignatureProviderAdapterAclBoundaryTest {

    private static final Set<Class<?>> ALLOWED_JDK_TYPES = Set.of(String.class, void.class, Void.class);
    private static final Set<String> ALLOWED_JDK_PACKAGES = Set.of("java.lang", "java.time", "java.util");

    @Test
    @DisplayName("every interface method uses only acl-package or plain JDK types")
    void interfaceSignaturesStayInsideTheAclPackage() {
        for (Method method : ESignatureProviderAdapter.class.getMethods()) {
            assertAclType(method.getReturnType(), method);
            for (Parameter parameter : method.getParameters()) {
                assertAclType(parameter.getType(), method);
            }
        }
    }

    @Test
    @DisplayName("the request/response records only nest acl-package or plain JDK types")
    void dataShapesStayInsideTheAclPackage() {
        assertThat(onlyAclOrJdkFields(SignatureRequest.class)).isTrue();
        assertThat(onlyAclOrJdkFields(SignerRequest.class)).isTrue();
        assertThat(onlyAclOrJdkFields(EnvelopeHandle.class)).isTrue();
        assertThat(onlyAclOrJdkFields(EnvelopeStatusSnapshot.class)).isTrue();
        assertThat(onlyAclOrJdkFields(ProviderSignerEvidence.class)).isTrue();
    }

    @Test
    @DisplayName("the stub adapter satisfies the full interface: send, poll, and void an envelope")
    void stubAdapterDrivesTheFullLifecycleThroughTheInterfaceAlone() {
        ESignatureProviderAdapter adapter = new StubESignatureProviderAdapter();

        EnvelopeHandle handle = adapter.sendForSignature(
                new SignatureRequest("ContractDocument", "doc-1", "MSA", List.of(
                        new SignerRequest("signer-1", "Ada Lovelace", "ada@example.com"))));
        assertThat(handle.providerEnvelopeRef()).isNotBlank();

        EnvelopeStatusSnapshot sent = adapter.getEnvelopeStatus(handle.providerEnvelopeRef());
        assertThat(sent.status()).isEqualTo(SignatureEnvelopeStatus.SENT);

        adapter.voidEnvelope(handle.providerEnvelopeRef(), "superseded by a new document version");

        EnvelopeStatusSnapshot voided = adapter.getEnvelopeStatus(handle.providerEnvelopeRef());
        assertThat(voided.status()).isEqualTo(SignatureEnvelopeStatus.VOIDED);
    }

    @Test
    @DisplayName("voiding an unknown envelope fails through SignatureProviderException, not a provider-specific type")
    void unknownEnvelopeFailsThroughTheAclException() {
        ESignatureProviderAdapter adapter = new StubESignatureProviderAdapter();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter.voidEnvelope("no-such-envelope", "x"))
                .isInstanceOf(SignatureProviderException.class);
    }

    private void assertAclType(Class<?> type, Method method) {
        assertThat(isAclOrJdkType(type))
                .as("%s on %s is not an acl-package or plain JDK type", type.getName(), method)
                .isTrue();
    }

    private boolean onlyAclOrJdkFields(Class<?> recordType) {
        for (var field : recordType.getDeclaredFields()) {
            Class<?> fieldType = field.getType();
            Class<?> elementType = List.class.isAssignableFrom(fieldType) ? extractListElementType(field) : fieldType;
            if (!isAclOrJdkType(elementType)) {
                return false;
            }
        }
        return true;
    }

    private Class<?> extractListElementType(java.lang.reflect.Field field) {
        if (field.getGenericType() instanceof java.lang.reflect.ParameterizedType parameterized
                && parameterized.getActualTypeArguments().length == 1
                && parameterized.getActualTypeArguments()[0] instanceof Class<?> element) {
            return element;
        }
        return Object.class;
    }

    private boolean isAclOrJdkType(Class<?> type) {
        if (ALLOWED_JDK_TYPES.contains(type) || type.isPrimitive()) {
            return true;
        }
        if (type == SignatureEnvelopeStatus.class) {
            // Our own provider-agnostic status vocabulary (an enum, never an entity), one package up from acl.
            // The adapter maps whatever the provider calls its statuses onto this; it is not a provider leak.
            return true;
        }
        String packageName = type.getPackageName();
        if (ALLOWED_JDK_PACKAGES.contains(packageName)) {
            return true;
        }
        return packageName.equals(ESignatureProviderAdapter.class.getPackageName());
    }
}
