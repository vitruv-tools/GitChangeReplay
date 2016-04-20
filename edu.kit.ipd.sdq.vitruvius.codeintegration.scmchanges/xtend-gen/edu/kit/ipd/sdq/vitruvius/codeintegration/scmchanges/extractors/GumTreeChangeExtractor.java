package edu.kit.ipd.sdq.vitruvius.codeintegration.scmchanges.extractors;

import com.github.gumtreediff.actions.RootsClassifier;
import com.github.gumtreediff.gen.jdt.JdtTreeGenerator;
import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.matchers.Matchers;
import com.github.gumtreediff.tree.ITree;
import com.github.gumtreediff.tree.TreeContext;
import com.github.gumtreediff.tree.TreeUtils;
import com.google.common.base.Objects;
import edu.kit.ipd.sdq.vitruvius.codeintegration.scmchanges.converters.GumTree2JdtAstConverterImpl;
import edu.kit.ipd.sdq.vitruvius.codeintegration.scmchanges.extractors.OrderByDstOrderComparator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.xtext.xbase.lib.Exceptions;

@SuppressWarnings("all")
public class GumTreeChangeExtractor {
  private final static Logger logger = Logger.getLogger(GumTreeChangeExtractor.class);
  
  private String oldContent;
  
  private String newContent;
  
  public GumTreeChangeExtractor(final String oldContent, final String newContent) {
    this.oldContent = oldContent;
    this.newContent = newContent;
    GumTreeChangeExtractor.logger.setLevel(Level.ALL);
  }
  
  public ArrayList<String> extract() {
    try {
      final JdtTreeGenerator generator = new JdtTreeGenerator();
      final TreeContext srcTreeContext = generator.generateFromString(this.oldContent);
      final TreeContext dstTreeContext = generator.generateFromString(this.newContent);
      GumTreeChangeExtractor.logger.info(" --- SOURCE TREE --- ");
      ITree _root = srcTreeContext.getRoot();
      String _prettyString = _root.toPrettyString(srcTreeContext);
      GumTreeChangeExtractor.logger.info(_prettyString);
      GumTreeChangeExtractor.logger.info(" --- DESTINATION TREE --- ");
      ITree _root_1 = dstTreeContext.getRoot();
      String _prettyString_1 = _root_1.toPrettyString(dstTreeContext);
      GumTreeChangeExtractor.logger.info(_prettyString_1);
      final ArrayList<String> contentList = new ArrayList<String>();
      final GumTree2JdtAstConverterImpl converter = new GumTree2JdtAstConverterImpl();
      ITree _root_2 = srcTreeContext.getRoot();
      CompilationUnit _convertTree = converter.convertTree(_root_2);
      String _string = _convertTree.toString();
      contentList.add(_string);
      Matchers _instance = Matchers.getInstance();
      ITree _root_3 = srcTreeContext.getRoot();
      ITree _root_4 = dstTreeContext.getRoot();
      final Matcher m = _instance.getMatcher(_root_3, _root_4);
      m.match();
      final MappingStore mappings = m.getMappings();
      final RootsClassifier classifier = new RootsClassifier(srcTreeContext, dstTreeContext, m);
      ITree _root_5 = srcTreeContext.getRoot();
      final ITree workingTree = _root_5.deepCopy();
      this.processDels(classifier, workingTree, mappings, contentList, converter);
      this.processAdds(classifier, workingTree, mappings, contentList, converter);
      this.processMvs(classifier, workingTree, mappings, contentList, converter);
      this.processUpds(classifier, workingTree, mappings, contentList, converter);
      return contentList;
    } catch (Throwable _e) {
      throw Exceptions.sneakyThrow(_e);
    }
  }
  
  public void processUpds(final RootsClassifier classifier, final ITree workingTree, final MappingStore mappings, final ArrayList<String> contentList, final GumTree2JdtAstConverterImpl converter) {
    Set<ITree> _srcUpdTrees = classifier.getSrcUpdTrees();
    final HashSet<ITree> rootUpds = this.getRootChanges(_srcUpdTrees);
    for (final ITree updTree : rootUpds) {
      {
        GumTreeChangeExtractor.logger.info("Found UPD");
        int _id = updTree.getId();
        final ITree updatedNodeInWorkingTree = this.findNodeWithId(workingTree, _id);
        final boolean removed = this.removeNodeFromWorkingTree(updatedNodeInWorkingTree, workingTree, mappings);
        final ITree updatedNodeInDstTree = mappings.getDst(updTree);
        final boolean added = this.addNodeToWorkingTree(updatedNodeInDstTree, workingTree, mappings);
        boolean _and = false;
        if (!added) {
          _and = false;
        } else {
          _and = removed;
        }
        if (_and) {
          CompilationUnit _convertTree = converter.convertTree(workingTree);
          String _string = _convertTree.toString();
          contentList.add(_string);
        } else {
          if ((!((!added) && (!removed)))) {
            GumTreeChangeExtractor.logger.warn("Couldn\'t add or remove but the other.");
          }
        }
      }
    }
  }
  
  public void processMvs(final RootsClassifier classifier, final ITree workingTree, final MappingStore mappings, final ArrayList<String> contentList, final GumTree2JdtAstConverterImpl converter) {
    Set<ITree> _srcMvTrees = classifier.getSrcMvTrees();
    final HashSet<ITree> rootMvs = this.getRootChanges(_srcMvTrees);
    for (final ITree mvTree : rootMvs) {
      {
        GumTreeChangeExtractor.logger.info("Found MV");
        int _id = mvTree.getId();
        final ITree movedNodeInWorkingTree = this.findNodeWithId(workingTree, _id);
        final boolean removed = this.removeNodeFromWorkingTree(movedNodeInWorkingTree, workingTree, mappings);
        final ITree movedNodeInDstTree = mappings.getDst(mvTree);
        final boolean added = this.addNodeToWorkingTree(movedNodeInDstTree, workingTree, mappings);
        boolean _and = false;
        if (!added) {
          _and = false;
        } else {
          _and = removed;
        }
        if (_and) {
          CompilationUnit _convertTree = converter.convertTree(workingTree);
          String _string = _convertTree.toString();
          contentList.add(_string);
        } else {
          if ((!((!added) && (!removed)))) {
            GumTreeChangeExtractor.logger.warn("Couldn\'t add or remove but the other.");
          }
        }
      }
    }
  }
  
  public void processAdds(final RootsClassifier classifier, final ITree workingTree, final MappingStore mappings, final ArrayList<String> contentList, final GumTree2JdtAstConverterImpl converter) {
    Set<ITree> _dstAddTrees = classifier.getDstAddTrees();
    final HashSet<ITree> rootAdds = this.getRootChanges(_dstAddTrees);
    for (final ITree addTree : rootAdds) {
      {
        GumTreeChangeExtractor.logger.info("Found ADD");
        boolean _addNodeToWorkingTree = this.addNodeToWorkingTree(addTree, workingTree, mappings);
        if (_addNodeToWorkingTree) {
          CompilationUnit _convertTree = converter.convertTree(workingTree);
          String _string = _convertTree.toString();
          contentList.add(_string);
        }
      }
    }
  }
  
  public void processDels(final RootsClassifier classifier, final ITree workingTree, final MappingStore mappings, final ArrayList<String> contentList, final GumTree2JdtAstConverterImpl converter) {
    Set<ITree> _srcDelTrees = classifier.getSrcDelTrees();
    final HashSet<ITree> rootDels = this.getRootChanges(_srcDelTrees);
    for (final ITree delTree : rootDels) {
      {
        GumTreeChangeExtractor.logger.info("Found DEL");
        boolean _removeNodeFromWorkingTree = this.removeNodeFromWorkingTree(delTree, workingTree, mappings);
        if (_removeNodeFromWorkingTree) {
          CompilationUnit _convertTree = converter.convertTree(workingTree);
          String _string = _convertTree.toString();
          contentList.add(_string);
        }
      }
    }
  }
  
  private HashSet<ITree> getRootChanges(final Set<ITree> allChanges) {
    final HashSet<ITree> rootChanges = new HashSet<ITree>();
    for (final ITree tree : allChanges) {
      ITree _parent = tree.getParent();
      boolean _contains = allChanges.contains(_parent);
      boolean _not = (!_contains);
      if (_not) {
        rootChanges.add(tree);
      }
    }
    return rootChanges;
  }
  
  public boolean addNodeToWorkingTree(final ITree addTree, final ITree workingTree, final MappingStore mappings) {
    final ITree addCopy = addTree.deepCopy();
    ITree dstParent = addTree.getParent();
    final ITree srcParent = mappings.getSrc(dstParent);
    boolean _notEquals = (!Objects.equal(srcParent, null));
    if (_notEquals) {
      final int parentId = srcParent.getId();
      final ITree parentInWorkingTree = this.findNodeWithId(workingTree, parentId);
      final List<ITree> children = parentInWorkingTree.getChildren();
      int pos = addTree.positionInParent();
      int _size = children.size();
      boolean _greaterThan = (pos > _size);
      if (_greaterThan) {
        int _size_1 = children.size();
        pos = _size_1;
      }
      children.add(pos, addCopy);
      OrderByDstOrderComparator _orderByDstOrderComparator = new OrderByDstOrderComparator(dstParent);
      children.sort(_orderByDstOrderComparator);
      parentInWorkingTree.setChildren(children);
      return true;
    } else {
      GumTreeChangeExtractor.logger.warn("Did not find parent in src. Should only happen for non-root changes, that we should exclude earlier.");
      return false;
    }
  }
  
  public boolean removeNodeFromWorkingTree(final ITree delTree, final ITree workingTree, final MappingStore mappings) {
    ITree srcParent = delTree.getParent();
    boolean _notEquals = (!Objects.equal(srcParent, null));
    if (_notEquals) {
      final int parentId = srcParent.getId();
      final ITree parentInWorkingTree = this.findNodeWithId(workingTree, parentId);
      boolean _equals = Objects.equal(parentInWorkingTree, null);
      if (_equals) {
        return false;
      }
      final List<ITree> children = parentInWorkingTree.getChildren();
      ITree toDelete = ((ITree) null);
      for (final ITree child : children) {
        int _id = child.getId();
        int _id_1 = delTree.getId();
        boolean _equals_1 = (_id == _id_1);
        if (_equals_1) {
          toDelete = child;
        }
      }
      boolean _equals_2 = Objects.equal(toDelete, null);
      if (_equals_2) {
        throw new RuntimeException("Could not find child.");
      }
      children.remove(toDelete);
      parentInWorkingTree.setChildren(children);
      return true;
    } else {
      GumTreeChangeExtractor.logger.warn("Did not find parent in src. Should only happen for non-root changes, that we should exclude earlier.");
      return false;
    }
  }
  
  public ITree findNodeWithId(final ITree tree, final int id) {
    List<ITree> _breadthFirst = TreeUtils.breadthFirst(tree);
    for (final ITree node : _breadthFirst) {
      int _id = node.getId();
      boolean _equals = (_id == id);
      if (_equals) {
        return node;
      }
    }
    GumTreeChangeExtractor.logger.warn("Could not find parent. Probably already deleted from working tree due to earlier operation.");
    return null;
  }
}
