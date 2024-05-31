package generate;

import com.intellij.ui.treeStructure.Tree;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.Stack;

class PdfStructureService {

  final DefaultTreeModel model;
  private final Stack<DefaultMutableTreeNode> lastParent = new Stack<>();

  public PdfStructureService() {
    lastParent.push(new DefaultMutableTreeNode("Mustache Tree Root"));
    model = new DefaultTreeModel(lastParent.peek());
    var tree = new Tree(model);
    tree.setRootVisible(false);
  }

  public record Structure(String name, int line, Structure[] structures) {
  }

}
