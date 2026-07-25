package com.devforge.workspace.contract;

/**
 * A member's authority within one workspace.
 *
 * <p>Roles are ranked rather than mapped to a permission matrix. Every capability
 * in the product so far is monotonic — anyone who can manage members can also
 * edit content — so a rank comparison expresses the rule exactly and adding a
 * tier does not require revisiting existing checks.
 */
public enum WorkspaceRole {

    /** Read-only access to documents and boards. */
    VIEWER(0),

    /** Can create and edit documents, boards, and tasks. */
    MEMBER(1),

    /** Can additionally add, remove, and re-role members. */
    ADMIN(2),

    /** Can additionally rename and delete the workspace itself. */
    OWNER(3);

    private final int rank;

    WorkspaceRole(int rank) {
        this.rank = rank;
    }

    /** @return whether this role carries at least the authority of {@code required} */
    public boolean atLeast(WorkspaceRole required) {
        return this.rank >= required.rank;
    }
}
