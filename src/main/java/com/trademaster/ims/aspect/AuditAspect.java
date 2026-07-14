package com.trademaster.ims.aspect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.trademaster.ims.annotation.Auditable;
import com.trademaster.ims.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;

@Aspect
@Component
public class AuditAspect {

    @Autowired
    private AuditLogService auditLogService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final ThreadLocal<Integer> adviceDepth = ThreadLocal.withInitial(() -> 0);
    private final ThreadLocal<Integer> auditCount = ThreadLocal.withInitial(() -> 0);

    @Around("@annotation(com.trademaster.ims.annotation.Auditable) || " +
            "(within(com.trademaster.ims.controller..*) && " +
            "(@annotation(org.springframework.web.bind.annotation.PostMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PutMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PatchMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.DeleteMapping)))")
    public Object logAudit(ProceedingJoinPoint joinPoint) throws Throwable {
        adviceDepth.set(adviceDepth.get() + 1);

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Auditable auditable = method.getAnnotation(Auditable.class);
        AuditMeta meta = resolveAuditMeta(method, signature.getDeclaringType(), auditable);

        if (meta == null) {
            return proceedAndCleanup(joinPoint);
        }

        HttpServletRequest request = getCurrentRequest();
        int auditCountBefore = auditCount.get();

        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable throwable) {
            cleanupThreadLocals();
            throw throwable;
        }

        if (meta.autoController && auditCount.get() > auditCountBefore) {
            cleanupThreadLocals();
            return result;
        }

        Object entity = unwrapResponseBody(result);
        String entityType = meta.autoController && entity != null && !isUserAudit(meta)
                ? inferEntityTypeFromEntity(entity, meta.entityType)
                : meta.entityType;
        Long entityId = extractEntityId(joinPoint, entity, entityType);
        String newValueJson = buildAuditNewValue(meta, method, joinPoint.getArgs(), entity);

        auditLogService.saveLog(
                getCurrentUserId(),
                getCurrentUsername(),
                meta.action,
                entityType,
                entityId,
                null,
                newValueJson,
                request
        );

        auditCount.set(auditCount.get() + 1);
        cleanupThreadLocals();
        return result;
    }

    private Object proceedAndCleanup(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            return joinPoint.proceed();
        } finally {
            cleanupThreadLocals();
        }
    }

    private void cleanupThreadLocals() {
        int depth = adviceDepth.get() - 1;
        if (depth <= 0) {
            adviceDepth.remove();
            auditCount.remove();
        } else {
            adviceDepth.set(depth);
        }
    }

    private AuditMeta resolveAuditMeta(Method method, Class<?> declaringType, Auditable auditable) {
        if (auditable != null) {
            return new AuditMeta(auditable.action(), auditable.entityType(), false);
        }

        if (!declaringType.getPackageName().startsWith("com.trademaster.ims.controller")) {
            return null;
        }
        if (isExcludedController(declaringType)) {
            return null;
        }

        AuditMeta profileMeta = resolveProfileAuditMeta(method, declaringType);
        if (profileMeta != null) {
            return profileMeta;
        }

        return new AuditMeta(inferAction(method), inferEntityTypeFromController(declaringType), true);
    }

    private AuditMeta resolveProfileAuditMeta(Method method, Class<?> declaringType) {
        if (!"ProfileController".equals(declaringType.getSimpleName())) {
            return null;
        }
        String methodName = method.getName();
        if ("requestPasswordChangeOtp".equals(methodName) || "changePassword".equals(methodName)) {
            return new AuditMeta("PASSWORD_CHANGE_OTP_REQUEST", "USER", true);
        }
        if ("confirmPasswordChange".equals(methodName)) {
            return new AuditMeta("PASSWORD_CHANGE_CONFIRMED", "USER", true);
        }
        if ("uploadPhoto".equals(methodName) || "updateMyProfile".equals(methodName) || "updateMySettings".equals(methodName)) {
            return new AuditMeta("UPDATE", "USER", true);
        }
        return null;
    }

    private boolean isExcludedController(Class<?> declaringType) {
        String simpleName = declaringType.getSimpleName();
        return "AuthController".equals(simpleName)
                || "AuditLogController".equals(simpleName)
                || "FileDownloadController".equals(simpleName);
    }

    private String inferAction(Method method) {
        String methodName = method.getName().toLowerCase();
        if (method.isAnnotationPresent(DeleteMapping.class)) {
            return "DELETE";
        }
        if (methodName.contains("approveandpost") || (methodName.contains("approve") && methodName.contains("post"))) return "APPROVE_POST";
        if (methodName.contains("returnforcorrection")) return "RETURN_FOR_CORRECTION";
        if (methodName.contains("requestpayment")) return "REQUEST_PAYMENT";
        if (methodName.contains("passwordchangeotp")) return "PASSWORD_CHANGE_OTP_REQUEST";
        if (methodName.contains("confirmpasswordchange")) return "PASSWORD_CHANGE_CONFIRMED";
        if (methodName.contains("approve")) return "APPROVE";
        if (methodName.contains("reject")) return "REJECT";
        if (methodName.contains("submit")) return "SUBMIT";
        if (methodName.contains("cancel")) return "CANCEL";
        if (methodName.contains("receive")) return "RECEIVE";
        if (methodName.contains("refund")) return "REFUND";
        if (methodName.contains("reversal") || methodName.contains("reverse")) return "REVERSE";
        if (methodName.contains("import")) return "IMPORT";
        if (methodName.contains("export")) return "EXPORT";
        if (methodName.contains("unmatch")) return "UNMATCH";
        if (methodName.contains("match")) return "MATCH";
        if (methodName.contains("reconcile")) return "RECONCILE";
        if (methodName.contains("status") || methodName.contains("mark")) return "STATUS_CHANGE";
        if (method.isAnnotationPresent(PutMapping.class) || method.isAnnotationPresent(PatchMapping.class)) return "UPDATE";
        return "CREATE";
    }

    private String inferEntityTypeFromController(Class<?> declaringType) {
        String simpleName = declaringType.getSimpleName();
        if (simpleName.endsWith("Controller")) {
            simpleName = simpleName.substring(0, simpleName.length() - "Controller".length());
        }
        return "Pos".equals(simpleName) ? "Sale" : simpleName;
    }

    private String inferEntityTypeFromEntity(Object entity, String fallback) {
        if (entity instanceof Iterable<?>) {
            Iterator<?> iterator = ((Iterable<?>) entity).iterator();
            if (iterator.hasNext()) {
                Object first = iterator.next();
                return first != null ? first.getClass().getSimpleName() : fallback;
            }
            return fallback;
        }
        return entity.getClass().getSimpleName();
    }

    private Object unwrapResponseBody(Object result) {
        if (result instanceof ResponseEntity<?>) {
            return ((ResponseEntity<?>) result).getBody();
        }
        return result;
    }

    private Long extractEntityId(ProceedingJoinPoint joinPoint, Object entity, String entityType) {
        if (joinPoint.getArgs().length > 0 && joinPoint.getArgs()[0] instanceof Long) {
            return (Long) joinPoint.getArgs()[0];
        }
        if (entity != null) {
            Long entityId = extractId(entity, entityType);
            if (entityId != null) {
                return entityId;
            }
        }
        Long currentUserId = getCurrentUserId();
        if ("USER".equalsIgnoreCase(entityType) && currentUserId != null) {
            return currentUserId;
        }
        return currentUserId != null ? currentUserId : 0L;
    }

    private Long extractId(Object entity, String entityType) {
        if (entity == null) return null;

        if (entity instanceof Iterable<?>) {
            Iterator<?> iterator = ((Iterable<?>) entity).iterator();
            return iterator.hasNext() ? extractId(iterator.next(), entityType) : null;
        }

        try {
            String getterName = "get" + entityType + "Id";
            Method method = entity.getClass().getMethod(getterName);
            Object idObj = method.invoke(entity);
            if (idObj instanceof Number) {
                return ((Number) idObj).longValue();
            }
        } catch (Exception e) { /* try next */ }

        for (Method method : entity.getClass().getMethods()) {
            if (method.getParameterCount() == 0
                    && method.getName().startsWith("get")
                    && method.getName().endsWith("Id")) {
                try {
                    Object idObj = method.invoke(entity);
                    if (idObj instanceof Number) {
                        return ((Number) idObj).longValue();
                    }
                } catch (Exception e) { /* try next */ }
            }
        }

        try {
            Method method = entity.getClass().getMethod("getId");
            Object idObj = method.invoke(entity);
            if (idObj instanceof Number) {
                return ((Number) idObj).longValue();
            }
        } catch (Exception e) { /* try next */ }

        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            Object idObj = field.get(entity);
            if (idObj instanceof Number) {
                return ((Number) idObj).longValue();
            }
        } catch (Exception e) {
            try {
                for (Field field : entity.getClass().getDeclaredFields()) {
                    if (field.getName().endsWith("Id")) {
                        field.setAccessible(true);
                        Object idObj = field.get(entity);
                        if (idObj instanceof Number) {
                            return ((Number) idObj).longValue();
                        }
                    }
                }
            } catch (Exception ex) { /* ignore */ }
        }
        return null;
    }

    private String toSafeJson(Object entity) throws Exception {
        JsonNode node = objectMapper.valueToTree(entity);
        maskSensitiveFields(node);
        return objectMapper.writeValueAsString(node);
    }

    private String toSafeJsonOrFallback(Object entity) {
        try {
            return toSafeJson(entity);
        } catch (Exception e) {
            return "{\"auditSerialization\":\"failed\",\"entityClass\":\""
                    + entity.getClass().getSimpleName()
                    + "\"}";
        }
    }

    private String buildAuditNewValue(AuditMeta meta, Method method, Object[] args, Object entity) {
        if ("PASSWORD_CHANGE_OTP_REQUEST".equals(meta.action)) {
            return "Password change OTP requested";
        }
        if ("PASSWORD_CHANGE_CONFIRMED".equals(meta.action)) {
            return "Password changed successfully";
        }
        return entity != null ? toSafeJsonOrFallback(entity) : buildRequestSummary(method, args);
    }

    private boolean isUserAudit(AuditMeta meta) {
        return "USER".equalsIgnoreCase(meta.entityType);
    }

    private void maskSensitiveFields(JsonNode node) {
        if (node == null) return;

        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            Iterator<String> fieldNames = objectNode.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                JsonNode child = objectNode.get(fieldName);
                String normalized = fieldName.toLowerCase();
                if (isSensitiveField(normalized)) {
                    objectNode.put(fieldName, "***MASKED***");
                } else {
                    maskSensitiveFields(child);
                }
            }
        } else if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            for (JsonNode child : arrayNode) {
                maskSensitiveFields(child);
            }
        }
    }

    private boolean isSensitiveField(String normalized) {
        return normalized.contains("password")
                || normalized.contains("token")
                || normalized.contains("secret")
                || normalized.contains("authorization")
                || normalized.contains("bearer")
                || normalized.contains("jwt")
                || normalized.contains("credential")
                || normalized.contains("apikey")
                || normalized.contains("api_key")
                || normalized.contains("otp")
                || normalized.contains("verificationcode")
                || normalized.contains("pin")
                || normalized.contains("cvv")
                || normalized.contains("cardnumber")
                || normalized.contains("cardsecret")
                || normalized.contains("encodednewpassword")
                || normalized.contains("currentpassword")
                || normalized.contains("newpassword")
                || normalized.contains("confirmpassword");
    }

    private String buildRequestSummary(Method method, Object[] args) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("controllerMethod", method.getName());
        node.put("argumentCount", args != null ? args.length : 0);
        if (args != null && args.length > 0 && args[0] instanceof Long) {
            node.put("pathId", (Long) args[0]);
        }
        return node.toString();
    }

    private HttpServletRequest getCurrentRequest() {
        try {
            return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        } catch (Exception e) {
            return null;
        }
    }

    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof com.trademaster.ims.security.UserDetailsImpl) {
            return ((com.trademaster.ims.security.UserDetailsImpl) principal).getUserId();
        }
        return null;
    }

    private String getCurrentUsername() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername();
        }
        return "system";
    }

    private static class AuditMeta {
        private final String action;
        private final String entityType;
        private final boolean autoController;

        private AuditMeta(String action, String entityType, boolean autoController) {
            this.action = action;
            this.entityType = entityType;
            this.autoController = autoController;
        }
    }
}
