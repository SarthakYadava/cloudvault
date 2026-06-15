package com.cloudvault.workspace;

import com.cloudvault.error.InvalidWorkspaceException;
import com.cloudvault.error.WorkspaceAccessException;
import com.cloudvault.error.WorkspaceNotFoundException;
import com.cloudvault.file.DownloadUrlResponse;
import com.cloudvault.file.FileResponse;
import com.cloudvault.file.FileService;
import com.cloudvault.file.StoredFile;
import com.cloudvault.file.StoredFileRepository;
import com.cloudvault.user.UserAccount;
import com.cloudvault.user.UserAccountRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMembershipRepository membershipRepository;
    private final DocumentRequestRepository requestRepository;
    private final UserAccountRepository userRepository;
    private final StoredFileRepository fileRepository;
    private final FileService fileService;

    public WorkspaceService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMembershipRepository membershipRepository,
            DocumentRequestRepository requestRepository,
            UserAccountRepository userRepository,
            StoredFileRepository fileRepository,
            FileService fileService
    ) {
        this.workspaceRepository = workspaceRepository;
        this.membershipRepository = membershipRepository;
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.fileRepository = fileRepository;
        this.fileService = fileService;
    }

    @Transactional
    public WorkspaceResponse create(UUID userId, CreateWorkspaceRequest request) {
        Workspace workspace = workspaceRepository.save(
                Workspace.create(request.name().trim(), userId)
        );
        WorkspaceMembership membership = membershipRepository.save(
                WorkspaceMembership.create(
                        workspace.getId(),
                        userId,
                        WorkspaceRole.OWNER
                )
        );
        return response(workspace, membership);
    }

    @Transactional(readOnly = true)
    public List<WorkspaceResponse> list(UUID userId) {
        List<WorkspaceMembership> memberships =
                membershipRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        Map<UUID, Workspace> workspaces = workspaceRepository.findAllById(
                        memberships.stream()
                                .map(WorkspaceMembership::getWorkspaceId)
                                .toList()
                )
                .stream()
                .collect(Collectors.toMap(Workspace::getId, Function.identity()));

        return memberships.stream()
                .filter(membership -> workspaces.containsKey(membership.getWorkspaceId()))
                .map(membership -> response(
                        workspaces.get(membership.getWorkspaceId()),
                        membership
                ))
                .toList();
    }

    @Transactional
    public void delete(UUID userId, UUID workspaceId) {
        WorkspaceMembership membership = requireMembership(userId, workspaceId);
        requireOwner(membership);
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(WorkspaceNotFoundException::new);
        workspaceRepository.delete(workspace);
    }

    @Transactional(readOnly = true)
    public List<WorkspaceMemberResponse> listMembers(UUID userId, UUID workspaceId) {
        requireMembership(userId, workspaceId);
        List<WorkspaceMembership> memberships =
                membershipRepository.findAllByWorkspaceIdOrderByCreatedAtAsc(workspaceId);
        Map<UUID, UserAccount> users = usersById(
                memberships.stream().map(WorkspaceMembership::getUserId).toList()
        );
        return memberships.stream()
                .filter(membership -> users.containsKey(membership.getUserId()))
                .map(membership -> WorkspaceMemberResponse.from(
                        membership,
                        users.get(membership.getUserId())
                ))
                .sorted(Comparator.comparing(response -> response.role().ordinal()))
                .toList();
    }

    @Transactional
    public WorkspaceMemberResponse addMember(
            UUID userId,
            UUID workspaceId,
            AddWorkspaceMemberRequest request
    ) {
        WorkspaceMembership actor = requireMembership(userId, workspaceId);
        requireOwner(actor);
        if (request.role() == WorkspaceRole.OWNER) {
            throw new InvalidWorkspaceException(
                    "New members can be added as STAFF or CLIENT."
            );
        }

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        UserAccount user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidWorkspaceException(
                        "No registered CloudVault user has that email address."
                ));
        if (membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, user.getId())) {
            throw new InvalidWorkspaceException(
                    "That user is already a member of this workspace."
            );
        }

        try {
            WorkspaceMembership membership = membershipRepository.saveAndFlush(
                    WorkspaceMembership.create(
                            workspaceId,
                            user.getId(),
                            request.role()
                    )
            );
            return WorkspaceMemberResponse.from(membership, user);
        } catch (DataIntegrityViolationException exception) {
            throw new InvalidWorkspaceException(
                    "That user is already a member of this workspace."
            );
        }
    }

    @Transactional(readOnly = true)
    public List<DocumentRequestResponse> listRequests(
            UUID userId,
            UUID workspaceId
    ) {
        requireMembership(userId, workspaceId);
        List<DocumentRequest> requests =
                requestRepository.findAllByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
        Map<UUID, StoredFile> submissions = filesById(
                requests.stream()
                        .map(DocumentRequest::getSubmittedFileId)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList()
        );
        Map<UUID, UserAccount> assignees = usersById(
                requests.stream()
                        .map(DocumentRequest::getAssignedTo)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList()
        );
        Map<UUID, UserAccount> submitters = usersById(
                submissions.values().stream()
                        .map(StoredFile::getOwnerId)
                        .distinct()
                        .toList()
        );
        return requests.stream()
                .map(request -> response(request, assignees, submissions, submitters))
                .toList();
    }

    @Transactional
    public DocumentRequestResponse createRequest(
            UUID userId,
            UUID workspaceId,
            CreateDocumentRequest request
    ) {
        WorkspaceMembership actor = requireMembership(userId, workspaceId);
        requireManager(actor);
        if (request.dueDate() != null && request.dueDate().isBefore(LocalDate.now())) {
            throw new InvalidWorkspaceException("The due date cannot be in the past.");
        }

        UserAccount assignee = resolveAssignee(workspaceId, request.assigneeEmail());
        DocumentRequest documentRequest = requestRepository.save(
                DocumentRequest.create(
                        workspaceId,
                        request.title().trim(),
                        normalizeDescription(request.description()),
                        assignee == null ? null : assignee.getId(),
                        userId,
                        request.dueDate()
                )
        );
        return DocumentRequestResponse.from(documentRequest, assignee, null, null);
    }

    @Transactional
    public DocumentRequestResponse uploadSubmission(
            UUID userId,
            UUID workspaceId,
            UUID requestId,
            MultipartFile file
    ) {
        WorkspaceMembership actor = requireMembership(userId, workspaceId);
        DocumentRequest documentRequest = requestRepository
                .findByIdAndWorkspaceId(requestId, workspaceId)
                .orElseThrow(WorkspaceNotFoundException::new);
        requireSubmissionAccess(userId, actor, documentRequest);
        if (documentRequest.getStatus() == DocumentRequestStatus.APPROVED) {
            throw new InvalidWorkspaceException(
                    "Reopen the approved request before uploading a replacement."
            );
        }

        FileResponse uploaded = fileService.upload(userId, file);
        DocumentRequest saved;
        try {
            documentRequest.attachSubmission(uploaded.id());
            saved = requestRepository.saveAndFlush(documentRequest);
        } catch (RuntimeException exception) {
            compensateSubmission(userId, uploaded.id(), exception);
            throw exception;
        }
        StoredFile submission = fileRepository.findById(uploaded.id()).orElseThrow();
        UserAccount submittedBy = userRepository.findById(userId).orElseThrow();
        UserAccount assignee = saved.getAssignedTo() == null
                ? null
                : userRepository.findById(saved.getAssignedTo()).orElse(null);
        return DocumentRequestResponse.from(
                saved,
                assignee,
                submission,
                submittedBy
        );
    }

    @Transactional
    public DownloadUrlResponse createSubmissionDownloadUrl(
            UUID userId,
            UUID workspaceId,
            UUID requestId
    ) {
        requireMembership(userId, workspaceId);
        DocumentRequest documentRequest = requestRepository
                .findByIdAndWorkspaceId(requestId, workspaceId)
                .orElseThrow(WorkspaceNotFoundException::new);
        if (documentRequest.getSubmittedFileId() == null) {
            throw new InvalidWorkspaceException(
                    "No file has been submitted for this request."
            );
        }
        return fileService.createAuthorizedDownloadUrl(
                userId,
                documentRequest.getSubmittedFileId()
        );
    }

    @Transactional
    public DocumentRequestResponse updateStatus(
            UUID userId,
            UUID workspaceId,
            UUID requestId,
            UpdateDocumentRequestStatus request
    ) {
        WorkspaceMembership actor = requireMembership(userId, workspaceId);
        DocumentRequest documentRequest = requestRepository
                .findByIdAndWorkspaceId(requestId, workspaceId)
                .orElseThrow(WorkspaceNotFoundException::new);

        switch (request.status()) {
            case SUBMITTED -> submit(userId, actor, documentRequest);
            case APPROVED -> {
                requireManager(actor);
                if (documentRequest.getStatus() != DocumentRequestStatus.SUBMITTED) {
                    throw new InvalidWorkspaceException(
                            "Only submitted requests can be approved."
                    );
                }
                if (documentRequest.getSubmittedFileId() == null) {
                    throw new InvalidWorkspaceException(
                            "A submitted file is required before approval."
                    );
                }
                documentRequest.markApproved();
            }
            case PENDING -> {
                requireManager(actor);
                documentRequest.reopen();
            }
        }

        UserAccount assignee = documentRequest.getAssignedTo() == null
                ? null
                : userRepository.findById(documentRequest.getAssignedTo()).orElse(null);
        StoredFile submission = documentRequest.getSubmittedFileId() == null
                ? null
                : fileRepository.findById(documentRequest.getSubmittedFileId()).orElse(null);
        UserAccount submittedBy = submission == null
                ? null
                : userRepository.findById(submission.getOwnerId()).orElse(null);
        return DocumentRequestResponse.from(
                requestRepository.save(documentRequest),
                assignee,
                submission,
                submittedBy
        );
    }

    private void submit(
            UUID userId,
            WorkspaceMembership actor,
            DocumentRequest request
    ) {
        if (request.getStatus() == DocumentRequestStatus.APPROVED) {
            throw new InvalidWorkspaceException(
                    "An approved request must be reopened before it can be submitted again."
            );
        }
        requireSubmissionAccess(userId, actor, request);
        if (request.getSubmittedFileId() == null) {
            throw new InvalidWorkspaceException(
                    "Upload a file before marking the request as submitted."
            );
        }
        request.markSubmitted();
    }

    private void requireSubmissionAccess(
            UUID userId,
            WorkspaceMembership actor,
            DocumentRequest request
    ) {
        if (request.getAssignedTo() != null
                && !request.getAssignedTo().equals(userId)
                && actor.getRole() == WorkspaceRole.CLIENT) {
            throw new WorkspaceAccessException(
                    "Clients can submit only document requests assigned to them."
            );
        }
    }

    private UserAccount resolveAssignee(UUID workspaceId, String assigneeEmail) {
        if (assigneeEmail == null || assigneeEmail.isBlank()) {
            return null;
        }
        UserAccount assignee = userRepository
                .findByEmail(assigneeEmail.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new InvalidWorkspaceException(
                        "The selected assignee is not a registered user."
                ));
        if (!membershipRepository.existsByWorkspaceIdAndUserId(
                workspaceId,
                assignee.getId()
        )) {
            throw new InvalidWorkspaceException(
                    "Document requests can only be assigned to workspace members."
            );
        }
        return assignee;
    }

    private WorkspaceMembership requireMembership(UUID userId, UUID workspaceId) {
        if (!workspaceRepository.existsById(workspaceId)) {
            throw new WorkspaceNotFoundException();
        }
        return membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(WorkspaceNotFoundException::new);
    }

    private void requireOwner(WorkspaceMembership membership) {
        if (membership.getRole() != WorkspaceRole.OWNER) {
            throw new WorkspaceAccessException(
                    "Only the workspace owner can manage members."
            );
        }
    }

    private void requireManager(WorkspaceMembership membership) {
        if (membership.getRole() == WorkspaceRole.CLIENT) {
            throw new WorkspaceAccessException(
                    "Only workspace owners and staff can manage document requests."
            );
        }
    }

    private WorkspaceResponse response(
            Workspace workspace,
            WorkspaceMembership membership
    ) {
        return new WorkspaceResponse(
                workspace.getId(),
                workspace.getName(),
                membership.getRole(),
                membershipRepository.countByWorkspaceId(workspace.getId()),
                requestRepository.countByWorkspaceIdAndStatus(
                        workspace.getId(),
                        DocumentRequestStatus.PENDING
                ),
                workspace.getCreatedAt()
        );
    }

    private Map<UUID, UserAccount> usersById(List<UUID> ids) {
        return userRepository.findAllById(ids)
                .stream()
                .collect(Collectors.toMap(UserAccount::getId, Function.identity()));
    }

    private Map<UUID, StoredFile> filesById(List<UUID> ids) {
        return fileRepository.findAllById(ids)
                .stream()
                .collect(Collectors.toMap(StoredFile::getId, Function.identity()));
    }

    private DocumentRequestResponse response(
            DocumentRequest request,
            Map<UUID, UserAccount> assignees,
            Map<UUID, StoredFile> submissions,
            Map<UUID, UserAccount> submitters
    ) {
        StoredFile submission = submissions.get(request.getSubmittedFileId());
        return DocumentRequestResponse.from(
                request,
                assignees.get(request.getAssignedTo()),
                submission,
                submission == null ? null : submitters.get(submission.getOwnerId())
        );
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }

    private void compensateSubmission(
            UUID ownerId,
            UUID fileId,
            RuntimeException originalException
    ) {
        try {
            fileService.discardUpload(ownerId, fileId);
        } catch (RuntimeException compensationException) {
            originalException.addSuppressed(compensationException);
        }
    }
}
