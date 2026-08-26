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
