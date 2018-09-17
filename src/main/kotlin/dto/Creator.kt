package dto

// spec : http://www.idpf.org/epub/20/spec/OPF_2.0.1_draft.htm#Section2.2.2
data class Creator(
        var fileAs: String,
        var role: RoleType
)