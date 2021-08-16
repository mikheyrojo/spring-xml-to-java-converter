package pro.akvel.spring.converter;

import com.sun.codemodel.JAnnotationUse;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JPackage;
import com.sun.codemodel.JVar;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import pro.akvel.spring.converter.generator.BeanData;
import pro.akvel.spring.converter.generator.ConfigurationData;
import pro.akvel.spring.converter.generator.param.ConstructIndexParam;
import pro.akvel.spring.converter.generator.param.ConstructorBeanParam;
import pro.akvel.spring.converter.generator.param.ConstructorConstantParam;
import pro.akvel.spring.converter.generator.param.ConstructorNullParam;
import pro.akvel.spring.converter.generator.param.ConstructorSubBeanParam;
import pro.akvel.spring.converter.generator.param.PropertyBeanParam;
import pro.akvel.spring.converter.generator.param.PropertyParam;
import pro.akvel.spring.converter.generator.param.PropertyValueParam;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Java configuration class generator
 *
 * @author akvel
 * @since 12.08.2020
 */
public class JavaConfigurationGenerator {


    private static final JCodeModel CODE_MODEL = new JCodeModel();


    public void generateClass(String packageName,
                              String classConfigurationName,
                              BeanData beanData,
                              String outputPath) {
        generateClass(packageName, classConfigurationName,
                ConfigurationData.builder()
                        .beans(List.of(beanData))
                        .build(),
                outputPath);
    }

    @SneakyThrows
    public void generateClass(String packageName,
                              String classConfigurationName,
                              ConfigurationData configurationData,
                              String outputPath) {
        // Instantiate a new JCodeModel

        // Create a new package
        JPackage jp = CODE_MODEL._package(packageName);

        // Create a new class
        JDefinedClass jc = jp._class(classConfigurationName);

        jc.annotate(Configuration.class);
        jc.javadoc().add("Generated Java based configuration");

        // Add get beans
        configurationData.getBeans().forEach(it -> {
            JClass beanClass = CODE_MODEL.ref(it.getClazzName());

            JMethod method = jc.method(JMod.PUBLIC,
                    CODE_MODEL.ref(it.getClazzName()), getMethodName(it.getClazzName(), it.getId()));

            addBeanAnnotation(configurationData, method, it);

            if (!it.getScope().isBlank()) {
                JAnnotationUse scopeAnnotation = method.annotate(Scope.class);
                scopeAnnotation.param("value", it.getScope());
            }

            if (it.getDependsOn() != null) {
                JAnnotationUse scopeAnnotation = method.annotate(DependsOn.class);
                var arrayParam = scopeAnnotation.paramArray("value");
                Arrays.stream(it.getDependsOn()).forEach(arrayParam::param);
            }

            if (it.getDescription() != null) {
                method.javadoc().add(it.getDescription());
            }

            if (it.isPrimary()) {
                method.annotate(Primary.class);
            }

            addMethodParams(it, method);


            JBlock body = method.body();

            if (constructorParamsOnly(it)) {
                JInvocation aNew = JExpr._new(beanClass);
                addParamToBeanConstructor(it, method, aNew);
                body._return(aNew);
            } else {
                JVar newBean = body.decl(beanClass, "bean", JExpr._new(beanClass));
                setProperties(newBean, it.getPropertyParams(), method);
                body._return(newBean);
            }
        });

        CODE_MODEL.build(new File(outputPath));
    }

    private static boolean constructorParamsOnly(BeanData it) {
        return it.getPropertyParams().isEmpty();
    }

    private void setProperties(JVar newBean, @Nullable List<PropertyParam> propertyParams, JMethod method) {
        propertyParams.forEach(it -> {
            if (it instanceof PropertyBeanParam) {
                PropertyBeanParam beanParam = (PropertyBeanParam) it;

                JInvocation invocation = method.body().invoke(newBean, getSetterName(beanParam.getName()));
                invocation.arg(method.params().stream()
                        .filter(itt -> itt.name().equals(beanParam.getRef()))
                        .findFirst().orElseThrow());
            }

            if (it instanceof PropertyValueParam) {
                PropertyValueParam valueParam = (PropertyValueParam) it;
                JInvocation invocation = method.body().invoke(newBean, getSetterName(valueParam.getName()));
                invocation.arg(JExpr.lit(valueParam.getValue()));
            }
        });
    }

    @NonNull
    private String getSetterName(String fieldName) {
        return "set" + StringUtils.capitalize(fieldName);
    }


    private void addParamToBeanConstructor(BeanData it, JMethod method, JInvocation aNew) {
        it.getConstructorParams()
                .stream()
                .filter(itt -> itt instanceof ConstructIndexParam)
                .map(itt -> (ConstructIndexParam) itt)
                .sorted(Comparator.comparingInt(v ->
                        Optional.ofNullable(v.getIndex()).orElse(Integer.MAX_VALUE)))
                .forEach(arg -> {
                    if (arg instanceof ConstructorBeanParam) {
                        ConstructorBeanParam beanParam = (ConstructorBeanParam) arg;

                        aNew.arg(method.params().stream()
                                .filter(itt -> itt.name().equals(beanParam.getRef()))
                                .findFirst().orElseThrow());
                    }

                    if (arg instanceof ConstructorNullParam) {
                        aNew.arg(JExpr._null());
                    }

                    if (arg instanceof ConstructorSubBeanParam) {
                        ConstructorSubBeanParam subBeanData = (ConstructorSubBeanParam) arg;

                        JClass subBeanClass = CODE_MODEL.ref(subBeanData.getBeanData().getClazzName());
                        JInvocation subBeanNew = JExpr._new(subBeanClass);
                        if (constructorParamsOnly(subBeanData.getBeanData())) {
                            addParamToBeanConstructor(subBeanData.getBeanData(), method, subBeanNew);
                            aNew.arg(subBeanNew);
                        } else {
                            JVar newBeanVar = method.body().decl(subBeanClass, "bean", subBeanNew);
                            setProperties(newBeanVar, subBeanData.getBeanData().getPropertyParams(), method);
                            addParamToBeanConstructor(subBeanData.getBeanData(), method, subBeanNew);
                            aNew.arg(newBeanVar);
                        }
                    }

                    if (arg instanceof ConstructorConstantParam) {
                        ConstructorConstantParam constant = (ConstructorConstantParam) arg;

                        if (Integer.class.getName().equals(constant.getType())) {
                            aNew.arg(JExpr.lit(Integer.parseInt(constant.getValue())));
                        } else if (Long.class.getName().equals(constant.getType())) {
                            aNew.arg(JExpr.lit(Long.parseLong(constant.getValue())));
                        } else {
                            aNew.arg(JExpr.lit(constant.getValue()));
                        }
                    }
                });
    }

    private void addMethodParams(BeanData beanData, JMethod method) {

        beanData.getConstructorParams().stream()
                .filter(it -> it instanceof ConstructorBeanParam)
                .map(it -> (ConstructorBeanParam) it)
                .forEach(arg -> {
                    JVar param = method.param(CODE_MODEL.ref(arg.getClassName()), arg.getRef());
                    if (arg.getRef() != null) {
                        param.annotate(Qualifier.class).param("value", arg.getRef());
                    }
                });

        //add all refs from subBeans
        beanData.getConstructorParams().stream()
                .filter(it -> it instanceof ConstructorSubBeanParam)
                .map(it -> (ConstructorSubBeanParam) it)
                .forEach(it ->
                        addMethodParams(it.getBeanData(), method)
                );


        beanData.getPropertyParams().stream()
                .filter(it -> it instanceof PropertyBeanParam)
                .map(it -> (PropertyBeanParam) it)
                .forEach(arg -> {
                    JVar param = method.param(CODE_MODEL.ref(arg.getClassName()), arg.getRef());
                    if (arg.getRef() != null) {
                        param.annotate(Qualifier.class).param("value", arg.getRef());
                    }
                });


    }

    private String getMethodName(@Nonnull String className, @Nullable String id) {
        if (id != null) {
            return id;
        }

        //pro.akvel.test.TestBean -> TestBean
        String methodName = className.substring(className.lastIndexOf(".") + 1);

        //TestBean -> testBean
        methodName = methodName.substring(0, 1).toLowerCase() + methodName.substring(1);

        return methodName;
    }


    private static void addBeanAnnotation(ConfigurationData configurationData, @Nonnull JMethod method, @Nonnull BeanData beanData) {
        JAnnotationUse beanAnnotation = method.annotate(Bean.class);

        if (beanData.getId() != null) {
            if (beanData.getInitMethodName() == null && beanData.getDestroyMethodName() == null) {
                beanAnnotation.param("value", beanData.getId());
            } else {
                beanAnnotation.param("name", beanData.getId());
            }
        }

        if (beanData.getInitMethodName() != null) {
            beanAnnotation.param("initMethod", beanData.getInitMethodName());
        } else if (configurationData.getDefaultBeanInitMethod() != null) {
            beanAnnotation.param("initMethod", configurationData.getDefaultBeanInitMethod());
            method.javadoc().add("initMethod added by default-init-method\n");
        }

        if (beanData.getDestroyMethodName() != null) {
            beanAnnotation.param("destroyMethod", beanData.getDestroyMethodName());
        } else if (configurationData.getDefaultBeanDestroyMethod() != null) {
            method.javadoc().add("destroyMethod added by bean element default-destroy-method\n");
            beanAnnotation.param("destroyMethod", configurationData.getDefaultBeanDestroyMethod());
        }
    }


}
