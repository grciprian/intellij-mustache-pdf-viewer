package generate;

import com.intellij.ui.treeStructure.Tree;
import com.samskivert.mustache.Mustache;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.Stack;

public class JBMustacheTreeProducerVisitor implements Mustache.Visitor {

  final DefaultTreeModel model;
  private final Stack<DefaultMutableTreeNode> lastParent = new Stack<>();

  public JBMustacheTreeProducerVisitor() {
    lastParent.push(new DefaultMutableTreeNode("Mustache Tree Root"));
    model = new DefaultTreeModel(lastParent.peek());
    var tree = new Tree(model);
    tree.setRootVisible(false);
  }

  @Override
  public void visitText(String s) {
    // TODO we do not the text while visiting
  }

  @Override
  public void visitVariable(String s) {
    System.out.println("VAR " + s);
//    model.insertNodeInto(new DefaultMutableTreeNode(new NodeObject(s)), lastParent.peek(), lastParent.peek().getChildCount());
  }

  @Override
  public boolean visitInclude(String s) { // add to lastParent and becomes parent
    System.out.println("INCLUDE " + s);

//    var newParent = new DefaultMutableTreeNode(new NodeObject(s));
//    model.insertNodeInto(newParent, lastParent.peek(), lastParent.peek().getChildCount());
//    lastParent.push(newParent);
    return true;
  }

  @Override
  public boolean visitSection(String s) { // add to lastParent and becomes parent
    System.out.println("SECTION " + s);

//    model.insertNodeInto(new DefaultMutableTreeNode(new NodeObject(s)), lastParent, lastParent.getChildCount());
    return true;
  }

  @Override
  public boolean visitInvertedSection(String s) { // add to lastParent and becomes parent
    System.out.println("INVERTED SECTION " + s);

//    model.insertNodeInto(new DefaultMutableTreeNode(new NodeObject("^" + s)), lastParent, lastParent.getChildCount());
    return true;
  }

  @Override
  public boolean visitParent(String name) {
    System.out.println("PARENT " + name);

    return true;
  }

  @Override
  public boolean visitBlock(String name) {
    System.out.println("BLOCK " + name);

    return true;
  }

  public record NodeObject(String name) {
  }
}
