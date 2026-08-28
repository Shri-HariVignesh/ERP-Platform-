/**
 * Deliberately flat and small: this is the navigation shell, dashboards and auth page, not a
 * translation of every form in the app. New keys extend the same two objects — nothing about
 * the shape needs to change to grow this later (see docs/STATE_CONTRACT.md-style scoping: land
 * the infra once, widen coverage in follow-ups rather than block the toggle on 100% coverage).
 */
const STRINGS = {
  en: {
    'lang.en': 'EN',
    'lang.hi': 'हिं',
    'workspace.student': 'Student Workspace',
    'workspace.staff': 'Staff Workspace',
    'action.signOut': 'Sign out',
    'action.signIn': 'Sign in',
    'action.seeAll': 'See all',
    'action.search': 'Search',
    'action.allNotifications': 'All notifications',
    'action.close': 'Close',

    'nav.dashboard': 'Dashboard',
    'nav.myRequests': 'My Requests',
    'nav.leave': 'Leave',
    'nav.internship': 'Internship',
    'nav.documents': 'Documents',
    'nav.academic': 'Academic',
    'nav.grievance': 'Grievance',
    'nav.myTasks': 'My Tasks',
    'nav.students': 'Students',
    'nav.attendance': 'Attendance',
    'nav.marksResults': 'Marks & Results',
    'nav.notifications': 'Notifications',

    'section.keyMetrics': 'Key metrics',
    'section.quickActions': 'Quick actions',
    'section.recentRequests': 'Recent requests',
    'section.waitingOnYou': 'Waiting on you',
    'section.recentActivity': 'Recent activity',
    'section.needsAttention': 'Needs your attention',

    'login.title': 'CampusOS',
    'login.subtitle': 'One sign-in for students and staff',
    'login.username': 'Username',
    'login.password': 'Password',

    'helper.button': 'Helper',
    'helper.title': 'Need help?',
    'helper.search': 'Search help topics…',

    'antiRagging.title': 'Anti-Ragging Affidavit — action needed',
    'antiRagging.body': 'As required under the UGC Regulations on Curbing the Menace of Ragging, please acknowledge the anti-ragging affidavit for this academic year.',
    'antiRagging.ack': 'I acknowledge',

    'filter.all': 'All',
    'tasks.subtitle': 'Every request in your scope whose current stage is waiting on a role you hold. One inbox for leave approvals, internship verification, the grievance desk and any office or institution work — the workflow decides, not a per-form screen.',
    'tasks.empty': 'Nothing here. Every request in your scope has moved on.',
    'task.notActionable': 'Waiting on another desk — nothing for you to do here.',
    'task.reasonPlaceholder': 'Reason (required — the student reads this)',
    'card.addNote': 'Add a note',
    'card.seeWhatHappened': 'See what happened',
    'card.steps': 'steps',
    'card.updated': 'updated',
    'timeline.skippedByAutomation': 'skipped by automation',
    'workflow.subtitle': 'The same inbox, narrowed to one workflow. The buttons come from the frozen transition matrix intersected with your roles, so you are only ever offered a move that is genuinely yours to make at this stage.',
    'workflow.emptyPrefix': 'No ',
    'workflow.emptySuffix': ' request is waiting on you.',

    'notif.newRequest': 'New request',
    'notif.approvalRequired': 'Approval required',
    'notif.workflowUpdate': 'Workflow update',
    'notif.academicWrite': 'Academic write',

    'unit.days': 'day(s)',
    'unit.weeks': 'week(s)',
    'payload.leave.suffix': 'leave',
    'payload.internship.noCertificate': 'no certificate',
    'payload.grievance.anonymous': 'anonymous',
    'payload.document.copySuffix': 'copy/copies',

    'artifact.attendance': 'Attendance',
    'artifact.daysMarkedApprovedLeave': 'Days marked APPROVED_LEAVE',
    'artifact.verificationId': 'Verification ID',
    'artifact.creditsAdded': 'Credits added to academic record',
    'artifact.certificatePublishedAs': 'Certificate published as',
    'artifact.serialNumber': 'Serial number',
    'artifact.generatedDocument': 'Generated document',
    'artifact.viewDownload': 'View / download',

    'stat.awaiting': 'Awaiting your approval',
    'stat.overdue': 'Overdue',
    'stat.resolvedWeek': 'Resolved this week',
    'stat.totalOpen': 'Total open',

    'section.needsAction': 'Needs action now',
    'section.waitingOnOthers': 'Waiting on others',
    'section.recentlyClosed': 'Recently closed',
    'section.needsAction.empty': 'All caught up — nothing needs you right now.',
    'section.waitingOnOthers.empty': 'Nothing waiting on someone else right now.',
    'section.recentlyClosed.empty': 'Nothing closed recently.',

    'toast.queuedPrefix': 'Done — ',
    'toast.undoingInPrefix': 'Undoing in ',
    'toast.undoingInSuffix': 's…',
    'toast.undo': 'Undo',
    'toast.undone': 'Undone.',
    'toast.alreadyHandled': 'Already handled — someone else acted on this first.',
    'toast.error': 'Something went wrong — the request was restored.',
    'toast.retry': 'Retry',
    'toast.reconciling': 'Finishing a pending action from last time…',

    'sort.label': 'Sort',
    'sort.urgency': 'Urgency',
    'sort.newest': 'Newest',
    'sort.type': 'Type',

    'cmdk.trigger': 'Search',
    'cmdk.placeholder': 'Search students, tasks, or jump to a page…',
    'cmdk.noResults': 'No matches.',
    'cmdk.students': 'Students',
    'cmdk.tasks': 'Tasks',
    'cmdk.goTo': 'Go to',
    'cmdk.hint': 'to search',
  },
  hi: {
    'lang.en': 'EN',
    'lang.hi': 'हिं',
    'workspace.student': 'छात्र कार्यक्षेत्र',
    'workspace.staff': 'स्टाफ़ कार्यक्षेत्र',
    'action.signOut': 'साइन आउट करें',
    'action.signIn': 'साइन इन करें',
    'action.seeAll': 'सभी देखें',
    'action.search': 'खोजें',
    'action.allNotifications': 'सभी सूचनाएं',
    'action.close': 'बंद करें',

    'nav.dashboard': 'डैशबोर्ड',
    'nav.myRequests': 'मेरे अनुरोध',
    'nav.leave': 'छुट्टी',
    'nav.internship': 'इंटर्नशिप',
    'nav.documents': 'दस्तावेज़',
    'nav.academic': 'शैक्षणिक',
    'nav.grievance': 'शिकायत',
    'nav.myTasks': 'मेरे कार्य',
    'nav.students': 'छात्र सूची',
    'nav.attendance': 'उपस्थिति',
    'nav.marksResults': 'अंक व परिणाम',
    'nav.notifications': 'सूचनाएं',

    'section.keyMetrics': 'मुख्य आंकड़े',
    'section.quickActions': 'त्वरित कार्य',
    'section.recentRequests': 'हाल के अनुरोध',
    'section.waitingOnYou': 'आपकी प्रतीक्षा में',
    'section.recentActivity': 'हाल की गतिविधि',
    'section.needsAttention': 'आपके ध्यान की आवश्यकता है',

    'login.title': 'कैंपसओएस',
    'login.subtitle': 'छात्रों और स्टाफ़ के लिए एक ही साइन-इन',
    'login.username': 'यूज़रनेम',
    'login.password': 'पासवर्ड',

    'helper.button': 'सहायक',
    'helper.title': 'मदद चाहिए?',
    'helper.search': 'मदद विषय खोजें…',

    'antiRagging.title': 'रैगिंग-विरोधी शपथ-पत्र — कार्रवाई आवश्यक',
    'antiRagging.body': 'रैगिंग की रोकथाम संबंधी UGC विनियमों के अनुसार, कृपया इस शैक्षणिक वर्ष के लिए रैगिंग-विरोधी शपथ-पत्र स्वीकार करें।',
    'antiRagging.ack': 'मैं स्वीकार करता/करती हूं',

    'filter.all': 'सभी',
    'tasks.subtitle': 'आपके दायरे में हर वह अनुरोध जिसका मौजूदा चरण आपकी किसी भूमिका की प्रतीक्षा में है। छुट्टी स्वीकृति, इंटर्नशिप सत्यापन, शिकायत डेस्क और किसी भी कार्यालय या संस्थान के कार्य के लिए एक ही इनबॉक्स — निर्णय वर्कफ़्लो लेता है, कोई अलग फ़ॉर्म स्क्रीन नहीं।',
    'tasks.empty': 'यहाँ कुछ नहीं है। आपके दायरे का हर अनुरोध आगे बढ़ चुका है।',
    'task.notActionable': 'किसी और डेस्क के पास प्रतीक्षारत — यहाँ आपके लिए कुछ नहीं है।',
    'task.reasonPlaceholder': 'कारण (आवश्यक — छात्र इसे पढ़ेगा)',
    'card.addNote': 'टिप्पणी जोड़ें',
    'card.seeWhatHappened': 'क्या हुआ देखें',
    'card.steps': 'चरण',
    'card.updated': 'अपडेट किया गया',
    'timeline.skippedByAutomation': 'स्वचालन द्वारा छोड़ा गया',
    'workflow.subtitle': 'वही इनबॉक्स, एक वर्कफ़्लो तक सीमित। बटन स्थिर ट्रांज़िशन मैट्रिक्स और आपकी भूमिकाओं के प्रतिच्छेदन से आते हैं, इसलिए आपको हमेशा केवल वही कदम दिखाया जाता है जो इस चरण पर वास्तव में आपका है।',
    'workflow.emptyPrefix': 'कोई ',
    'workflow.emptySuffix': ' अनुरोध आपकी प्रतीक्षा में नहीं है।',

    'notif.newRequest': 'नया अनुरोध',
    'notif.approvalRequired': 'स्वीकृति आवश्यक',
    'notif.workflowUpdate': 'वर्कफ़्लो अपडेट',
    'notif.academicWrite': 'शैक्षणिक प्रविष्टि',

    'unit.days': 'दिन',
    'unit.weeks': 'सप्ताह',
    'payload.leave.suffix': 'छुट्टी',
    'payload.internship.noCertificate': 'कोई प्रमाणपत्र नहीं',
    'payload.grievance.anonymous': 'गुमनाम',
    'payload.document.copySuffix': 'प्रति(यां)',

    'artifact.attendance': 'उपस्थिति',
    'artifact.daysMarkedApprovedLeave': 'स्वीकृत-छुट्टी के रूप में चिह्नित दिन',
    'artifact.verificationId': 'सत्यापन आईडी',
    'artifact.creditsAdded': 'शैक्षणिक रिकॉर्ड में जोड़े गए क्रेडिट',
    'artifact.certificatePublishedAs': 'प्रमाणपत्र इस रूप में प्रकाशित',
    'artifact.serialNumber': 'क्रम संख्या',
    'artifact.generatedDocument': 'तैयार दस्तावेज़',
    'artifact.viewDownload': 'देखें / डाउनलोड करें',

    'stat.awaiting': 'आपकी स्वीकृति की प्रतीक्षा में',
    'stat.overdue': 'समय-सीमा पार',
    'stat.resolvedWeek': 'इस सप्ताह हल किए गए',
    'stat.totalOpen': 'कुल खुले',

    'section.needsAction': 'अभी कार्रवाई आवश्यक',
    'section.waitingOnOthers': 'अन्य की प्रतीक्षा में',
    'section.recentlyClosed': 'हाल ही में बंद किए गए',
    'section.needsAction.empty': 'सब कुछ पूरा है — अभी आपके लिए कुछ नहीं है।',
    'section.waitingOnOthers.empty': 'फ़िलहाल किसी और की प्रतीक्षा में कुछ नहीं है।',
    'section.recentlyClosed.empty': 'हाल ही में कुछ भी बंद नहीं हुआ।',

    'toast.queuedPrefix': 'हो गया — ',
    'toast.undoingInPrefix': 'पूर्ववत करें: ',
    'toast.undoingInSuffix': 'सेकंड…',
    'toast.undo': 'पूर्ववत करें',
    'toast.undone': 'पूर्ववत किया गया।',
    'toast.alreadyHandled': 'पहले ही निपटाया जा चुका — किसी और ने इस पर पहले कार्रवाई कर दी।',
    'toast.error': 'कुछ गड़बड़ हो गई — अनुरोध वापस कर दिया गया।',
    'toast.retry': 'पुनः प्रयास करें',
    'toast.reconciling': 'पिछली बार का लंबित कार्य पूरा किया जा रहा है…',

    'sort.label': 'क्रमबद्ध करें',
    'sort.urgency': 'तात्कालिकता',
    'sort.newest': 'नवीनतम',
    'sort.type': 'प्रकार',

    'cmdk.trigger': 'खोजें',
    'cmdk.placeholder': 'छात्र, कार्य खोजें, या किसी पृष्ठ पर जाएं…',
    'cmdk.noResults': 'कोई मेल नहीं मिला।',
    'cmdk.students': 'छात्र',
    'cmdk.tasks': 'कार्य',
    'cmdk.goTo': 'यहाँ जाएं',
    'cmdk.hint': 'खोजने के लिए',
  },
};

/**
 * Helper-widget topic lists. Kept separate from STRINGS (flat key→string) because each entry
 * is a {q, a, href} record, and student/staff need different topics, not just different
 * words for the same ones.
 */
const HELPER_TOPICS = {
  en: {
    student: [
      { q: 'Apply for leave', a: 'Submit dates and a reason — routed to your advisor automatically.', href: '/leave' },
      { q: 'Check attendance', a: 'Your live percentage is on the Dashboard and under Academic.', href: '/academic' },
      { q: 'Download hall ticket', a: 'Released hall tickets appear under Documents once issued.', href: '/documents' },
      { q: 'Track a request', a: 'Every leave, document, internship and grievance in one tracker.', href: '/requests' },
      { q: 'Submit an internship', a: 'One faculty verification, one approval, then it is on your record.', href: '/internship' },
      { q: 'Raise a grievance', a: 'Auto-assigned to the right desk by category.', href: '/grievance' },
    ],
    staff: [
      { q: 'Review pending approvals', a: 'Everything waiting on your role, across every workflow.', href: '/faculty/tasks' },
      { q: 'Mark attendance', a: 'Pick a class and subject, then mark present/absent — or bulk-mark the whole class.', href: '/faculty/attendance' },
      { q: 'Enter marks', a: 'Save as draft any time; finalizing publishes it to the student.', href: '/faculty/marks' },
      { q: 'Find a student', a: 'Search your roster by name or roll number, sortable by either.', href: '/faculty/students' },
      { q: 'Approve leave', a: 'Leave requests routed to you appear in My Tasks with approve/reject actions.', href: '/faculty/leave' },
    ],
  },
  hi: {
    student: [
      { q: 'छुट्टी के लिए आवेदन करें', a: 'तारीखें और कारण दें — यह स्वतः आपके सलाहकार को भेज दिया जाता है।', href: '/leave' },
      { q: 'उपस्थिति देखें', a: 'आपका मौजूदा प्रतिशत डैशबोर्ड और शैक्षणिक अनुभाग में उपलब्ध है।', href: '/academic' },
      { q: 'हॉल टिकट डाउनलोड करें', a: 'जारी हॉल टिकट दस्तावेज़ अनुभाग में दिखते हैं।', href: '/documents' },
      { q: 'अनुरोध की स्थिति देखें', a: 'हर छुट्टी, दस्तावेज़, इंटर्नशिप और शिकायत एक ही ट्रैकर में।', href: '/requests' },
      { q: 'इंटर्नशिप जमा करें', a: 'एक फैकल्टी सत्यापन, एक स्वीकृति, फिर यह आपके रिकॉर्ड में आ जाता है।', href: '/internship' },
      { q: 'शिकायत दर्ज करें', a: 'श्रेणी के अनुसार सही डेस्क को स्वतः सौंपी जाती है।', href: '/grievance' },
    ],
    staff: [
      { q: 'लंबित स्वीकृतियाँ देखें', a: 'आपकी भूमिका पर लंबित सब कुछ, हर वर्कफ़्लो में।', href: '/faculty/tasks' },
      { q: 'उपस्थिति दर्ज करें', a: 'कक्षा और विषय चुनें, फिर उपस्थित/अनुपस्थित चिह्नित करें — या पूरी कक्षा को एक साथ।', href: '/faculty/attendance' },
      { q: 'अंक दर्ज करें', a: 'ड्राफ़्ट कभी भी सहेजें; अंतिम रूप देने पर यह छात्र को दिखता है।', href: '/faculty/marks' },
      { q: 'छात्र खोजें', a: 'नाम या रोल नंबर से अपनी सूची खोजें, दोनों से क्रमबद्ध।', href: '/faculty/students' },
      { q: 'छुट्टी स्वीकृत करें', a: 'आपको भेजे गए छुट्टी अनुरोध मेरे कार्य में स्वीकृति/अस्वीकृति के साथ दिखते हैं।', href: '/faculty/leave' },
    ],
  },
};

export const I18n = {
  SUPPORTED: Object.keys(STRINGS),
  t(locale, key) {
    const dict = STRINGS[locale] || STRINGS.en;
    return dict[key] ?? STRINGS.en[key] ?? key;
  },
  helperTopics(locale, audience) {
    const dict = HELPER_TOPICS[locale] || HELPER_TOPICS.en;
    return dict[audience] || [];
  },
  /**
   * For embedding inside <script type="application/json">. `<` is escaped so a topic string
   * can never contain a literal "</script" and prematurely close the element — moot for the
   * current static, developer-authored topic list, but cheap insurance if that ever changes.
   */
  helperTopicsJson(locale, audience) {
    return JSON.stringify(this.helperTopics(locale, audience)).replace(/</g, '\\u003c');
  },
};
