package danbikel.parser;

import danbikel.util.*;
import danbikel.lisp.*;
import java.util.*;

/**
 * An implementation of a constraint to sit in a tree structure of constraints
 * that represent a particular, unlexicalized tree, constraining a decoder
 * to only pursue theories consistent with that unlexicalized tree.
 */
public class UnlexTreeConstraint implements Constraint, SexpConvertible {
  private UnlexTreeConstraint parent = null;
  private List children = Collections.EMPTY_LIST;
  private Symbol label;
  private int start;
  private int end;

  public UnlexTreeConstraint(Sexp tree) {
    this(null, tree, new IntCounter(0));
  }

  private UnlexTreeConstraint(UnlexTreeConstraint parent,
                              Sexp tree, IntCounter currWordIdx) {
    if (Language.treebank.isPreterminal(tree)) {
      this.parent = parent;
      Word word = Language.treebank.makeWord(tree);
      label = word.tag();
      start = end = currWordIdx.get();
      currWordIdx.increment();
    }
    else {
      SexpList treeList = tree.list();
      int treeListLen = treeList.length();

      label = treeList.symbolAt(0);
      start = currWordIdx.get();
      this.parent = parent;
      children = new ArrayList(treeListLen - 1);
      for (int i = 1; i < treeListLen; i++) {
        Sexp child = treeList.get(i);
        children.add(new UnlexTreeConstraint(this, child, currWordIdx));
      }
      end = currWordIdx.get() - 1;
    }
  }

  public boolean isLeaf() { return children.size() == 0; }

  public boolean isSatisfiedByChild(Item childItem) {
    return (childItem.getConstraint().getParent() == this &&
            children.contains(childItem.getConstraint()));
  }

  public Constraint getParent() { return parent; }
  protected List getChildren() { return children; }
  public Symbol label() { return label; }
  public int start() { return start; }
  public int end() { return end; }

  public boolean isViolatedBy(Item item) {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns <code>true</code> if this constraint is satisfied by its local
   * information and either
   * <ul>
   * <li>the specified item represents a preterminal or
   * <li>the constraints of the specified item's children are identical
   * to the children of this constraint, and are in the same order
   * </ul>
   * More formally, let us define the term <i>nuclear family</i> of a node
   * in a tree to refer to the node itself and its (immediately dominated)
   * sequence of children.  Given that chart items form a tree structure,
   * let us also define an <i>item-induced constraint tree</i> as the tree
   * of constraints induced by mapping the nodes of a tree of chart items
   * to their assigned constraints.  Let <tt>c</tt> be the nuclear family
   * of this constraint and let <tt>t</tt> be the nuclear family of the
   * item-induced constraint tree of the specified item.  This method
   * returns <code>true</code> if this constraint is satisfied by its local
   * information and if <tt>c</tt> is identical to <tt>t</tt>.
   *
   * @param item the item to test for satisfaction by this constraint
   * @return whether this constraint is satisfied the specified item
   *
   * @see #isLocallySatisfiedBy(Item)
   */
  public boolean isSatisfiedBy(Item item) {
    CKYItem ckyItem = (CKYItem)item;

    // normally, preterminal items should be assigned constraints from the
    // list of leaves, and thus this case should normally not be considered;
    // but just in case a brain-dead programmer creates a decoder that is
    // inefficient in this way, here's the code to deal with it
    if (ckyItem.isPreterminal())
      return isLocallySatisfiedBy(item);

    if (!isLocallySatisfiedBy(item))
      return false;

    // now, make sure that number of children equals number of child constraints
    int numLeftChildren = ckyItem.numLeftChildren();
    int numRightChildren = ckyItem.numRightChildren();
    if (numLeftChildren + numRightChildren + 1 != children.size())
      return false;
    // check that each child constraint is met in proper order
    // first, check left children
    int constraintIdx = 0;
    for (SLNode curr = ckyItem.children(Constants.LEFT);
         curr != null && constraintIdx < children.size();
         curr = curr.next(), constraintIdx++) {
      Constraint currConstraint = ((CKYItem)curr.data()).getConstraint();
      if (currConstraint != children.get(constraintIdx))
        return false;
    }
    // now, check head child
    int headChildIdx = numLeftChildren;
    if (ckyItem.headChild().getConstraint() != children.get(headChildIdx))
      return false;
    // finally, check right children
    constraintIdx = children.size() - 1;
    for (SLNode curr = ckyItem.children(Constants.RIGHT);
         curr != null && constraintIdx >= 0;
         curr = curr.next(), constraintIdx--) {
      Constraint currConstraint = ((CKYItem)curr.data()).getConstraint();
      if (currConstraint != children.get(constraintIdx))
        return false;
    }
    return true;
  }

  public boolean isLocallySatisfiedBy(Item item) {
    CKYItem ckyItem = (CKYItem)item;
    return (ckyItem.label() == label &&
            ckyItem.start() == start &&
            ckyItem.end() == end);
  }

  public Sexp toSexp() {
    SexpList retVal = new SexpList(children.size() + 1);

    SexpList thisNode =
      new SexpList(3).add(label).add(Symbol.get(start)).add(Symbol.get(end));

    retVal.add(thisNode);
    for (int i = 0; i < children.size(); i++)
      retVal.add(((SexpConvertible)children.get(i)).toSexp());

    return retVal;
  }
}
