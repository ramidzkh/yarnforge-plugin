package me.ramidzkh.yarnforge.patch;

import org.cadixdev.mercury.RewriteContext;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import java.util.Arrays;
import java.util.Optional;

public class OnlyInVisitor extends ASTVisitor {

    private static final String ONLY_IN = "net.minecraftforge.api.distmarker.OnlyIn";
    private static final String ONLY_INS = "net.minecraftforge.api.distmarker.OnlyIns";

    private final RewriteContext context;

    public OnlyInVisitor(RewriteContext context) {
        this.context = context;
    }

    @Override
    public boolean visit(NormalAnnotation node) {
        return visitNode(node);
    }

    @Override
    public boolean visit(SingleMemberAnnotation node) {
        return visitNode(node);
    }

    private boolean visitNode(Annotation annotation) {
        IAnnotationBinding binding = annotation.resolveAnnotationBinding();
        String binaryName = binding.getAnnotationType().getBinaryName();

        if (ONLY_IN.equals(binaryName)) {
            AST ast = annotation.getAST();
            Dist dist = findPair(binding, "value").map(value -> Dist.valueOf(((IVariableBinding) value).getName())).get();
            Optional<ITypeBinding> _itf = findPair(binding, "_interface").map(ITypeBinding.class::cast);

            if (_itf.isPresent()) {
                ITypeBinding itf = _itf.get();

                NormalAnnotation newAnnotation = ast.newNormalAnnotation();
                newAnnotation.setTypeName(ast.newName(context.createImportRewrite().addImport("net.fabricmc.api.EnvironmentInterface")));

                {
                    ListRewrite listRewrite = context.createASTRewrite().getListRewrite(newAnnotation, NormalAnnotation.VALUES_PROPERTY);

                    {
                        MemberValuePair node = ast.newMemberValuePair();
                        node.setName(ast.newSimpleName("value"));
                        node.setValue(ast.newQualifiedName(ast.newName(context.createImportRewrite().addImport("net.fabricmc.api.EnvType")), ast.newSimpleName(dist.fabric)));
                        listRewrite.insertFirst(node, null);
                    }

                    {
                        MemberValuePair node = ast.newMemberValuePair();
                        node.setName(ast.newSimpleName("itf"));

                        {
                            TypeLiteral typeLiteral = ast.newTypeLiteral();
                            typeLiteral.setType(ast.newSimpleType(ast.newName(context.createImportRewrite().addImport(itf))));
                            node.setValue(typeLiteral);
                        }

                        listRewrite.insertLast(node, null);
                    }
                }

                context.createASTRewrite().replace(annotation, newAnnotation, null);
            } else {
                SingleMemberAnnotation newAnnotation = ast.newSingleMemberAnnotation();
                newAnnotation.setTypeName(ast.newName(context.createImportRewrite().addImport("net.fabricmc.api.Environment")));
                newAnnotation.setValue(ast.newQualifiedName(ast.newName(context.createImportRewrite().addImport("net.fabricmc.api.EnvType")), ast.newSimpleName(dist.fabric)));
                context.createASTRewrite().replace(annotation, newAnnotation, null);
            }
        } else if (ONLY_INS.equals(binaryName)) {
            // TODO: Rewrite these
        }

        return false;
    }

    private static Optional<Object> findPair(IAnnotationBinding annotation, String name) {
        return Arrays.stream(annotation.getDeclaredMemberValuePairs())
                .filter(pair -> pair.getName().equals(name))
                .findAny()
                .map(IMemberValuePairBinding::getValue);
    }

    private enum Dist {
        CLIENT("CLIENT"),
        DEDICATED_SERVER("SERVER");

        private final String fabric;

        Dist(String fabric) {
            this.fabric = fabric;
        }
    }
}
