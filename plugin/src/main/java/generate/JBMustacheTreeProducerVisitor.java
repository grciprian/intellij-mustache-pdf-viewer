package generate;

import com.intellij.ui.treeStructure.Tree;
import com.samskivert.mustache.Mustache;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

public class JBMustacheTreeProducerVisitor implements Mustache.Visitor {

  final DefaultTreeModel model;
  private Stack lastParent = new DefaultMutableTreeNode("Mustache Tree Root");

  public JBMustacheTreeProducerVisitor() {
    model = new DefaultTreeModel(lastParent);
    var tree = new Tree(model);
    tree.setRootVisible(false);
  }

  @Override
  public void visitText(String s) {
    // TODO we do not the text while visiting
  }

  @Override
  public void visitVariable(String s) {
    model.insertNodeInto(new DefaultMutableTreeNode(new NodeObject(s)), lastParent, lastParent.getChildCount());
  }

  @Override
  public boolean visitInclude(String s) { // add to lastParent and becomes parent
    var newParent = new DefaultMutableTreeNode(new NodeObject(s));
    model.insertNodeInto(newParent, lastParent, lastParent.getChildCount());
    lastParent = newParent;
    return true;
  }

  @Override
  public boolean visitSection(String s) {
    model.insertNodeInto(new DefaultMutableTreeNode(new NodeObject(s)), lastParent, lastParent.getChildCount());
    return true;
  }

  @Override
  public boolean visitInvertedSection(String s) {
    model.insertNodeInto(new DefaultMutableTreeNode(new NodeObject(s)), lastParent, lastParent.getChildCount());
    return true;
  }

  public record NodeObject(String name) {
  }
}
