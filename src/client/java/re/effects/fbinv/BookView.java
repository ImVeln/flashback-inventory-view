package re.effects.fbinv;

public interface BookView {
    int fbinv$tab();

    int fbinv$page();

    String fbinv$search();

    void fbinv$restore(InvUi ui);
}
