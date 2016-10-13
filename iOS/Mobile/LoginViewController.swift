//
//  LoginViewController.swift
//  Mobile
//
//  Created by Jason Hocker on 5/16/16.
//  Copyright © 2016 Ellucian Company L.P. and its affiliates. All rights reserved.
//

import LocalAuthentication

class LoginViewController : UIViewController, LoginProtocol {
    
    @IBOutlet weak var usernameField: UITextField!
    @IBOutlet weak var passwordField: UITextField!
    @IBOutlet weak var cancelButton: UIButton!
    @IBOutlet weak var signInButton: UIButton!
    @IBOutlet weak var rememberUserSwitch: UISwitch!
    @IBOutlet weak var useFingerprintSwitch: UISwitch!
    @IBOutlet weak var activityIndicator: UIActivityIndicatorView!
    @IBOutlet weak var useFingerprintSwitchLabel: UILabel!
    var url : String?
    var httpResponse : HTTPURLResponse?
    var canceled = false
    var completionBlock: (() -> Void)?
    var allowTouchId = false
    
    override func viewDidLoad() {
        super.viewDidLoad()
        self.usernameField.leftView = UIImageView(image: UIImage(named: "login_username"))
        self.usernameField.leftViewMode = .always
        self.passwordField.leftView = UIImageView(image: UIImage(named: "login_password"))
        self.passwordField.leftViewMode = .always
        self.signInButton.addBorderAndColor()
        self.cancelButton.addBorderAndColor()
        self.url = AppGroupUtilities.userDefaults()?.string(forKey: "login-url")
        self.usernameField.text = CurrentUser.sharedInstance.userauth
        self.rememberUserSwitch.isOn = CurrentUser.sharedInstance.remember
        self.useFingerprintSwitch.isOn = CurrentUser.sharedInstance.useFingerprint
        
        self.rememberUserSwitch.addTarget(self, action: #selector(LoginViewController.rememberUserSwitchToggled), for: .valueChanged)
        self.useFingerprintSwitch.addTarget(self, action: #selector(LoginViewController.useFingerprintSwitchLabelToggled), for: .valueChanged)
        
        evaluateTouchID()
    }
    
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        self.sendView( "Sign In Page", moduleName: nil)
    }
    
    @IBAction func signInCanceled(_ sender: AnyObject) {
        CurrentUser.sharedInstance.useFingerprint = false
        self.activityIndicator.stopAnimating()
        self.canceled = true
        self.sendEvent(category: .authentication, action: .cancel, label: "Click Cancel")
        //For cases where the user was previously signed in and timedout and canceled the prompt
        CurrentUser.sharedInstance.logoutWithoutUpdatingUI()
        NotificationCenter.default.post(name: CurrentUser.SignInReturnToHomeNotification, object: nil)
        self.dismiss(animated: true, completion: { _ in })
    }
    
    @IBAction func textFieldDoneEditing(_ sender: AnyObject) {
        let _ = sender.resignFirstResponder()
        self.signIn(sender)
    }
    
    @IBAction func progressToPasswordField(_ sender: AnyObject) {
        self.usernameField.resignFirstResponder()
        self.passwordField.becomeFirstResponder()
    }
    
    @IBAction func signIn(_ sender: AnyObject) {
        DispatchQueue.main.async(execute: {() -> Void in
            self.activityIndicator.startAnimating()
        })
        if self.rememberUserSwitch.isOn {
            self.sendEvent(category: .authentication, action: .login, label: "Authentication with save credential", moduleName: nil)
        } else if self.useFingerprintSwitch.isOn {
            self.sendEvent(category: .authentication, action: .login, label: "Authentication with use fingerprint", moduleName: nil)
        }
        else {
            self.sendEvent(category: .authentication, action: .login, label: "Authentication without save credential", moduleName: nil)
        }
        self.signInButton.isEnabled = false
        
        DispatchQueue.global(qos: .userInitiated).async(execute: {() -> Void in
            self.httpResponse = self.performLogin(self.url!, forUser: self.usernameField.text!, andPassword: self.passwordField.text!, andRememberUser: self.rememberUserSwitch.isOn, fingerprint: self.useFingerprintSwitch.isOn)
            
            let canceled: Bool = self.canceled
            let activityIndicator: UIActivityIndicatorView = self.activityIndicator
            DispatchQueue.main.async(execute: {() -> Void in
                activityIndicator.stopAnimating()
                if canceled {
                    return
                }
                else if let httpResponse = self.httpResponse , httpResponse.statusCode == 200 {
                    if let completionBlock = self.completionBlock {
                        completionBlock()
                    }
                    self.dismiss(animated: true, completion: { _ in })
                }
                else {
                    let alertController: UIAlertController = UIAlertController(title: NSLocalizedString("Sign In Failed", comment: "title for failed sign in"), message: NSLocalizedString("The password or user name you entered is incorrect. Please try again.", comment: "message for failed sign in"), preferredStyle: .alert)
                    let okAction = UIAlertAction(title: NSLocalizedString("OK", comment: "OK action"), style: .default, handler: {(action: UIAlertAction) -> Void in
                        self.signInButton.isEnabled = true
                    })
                    alertController.addAction(okAction)
                    self.present(alertController, animated: true, completion: { _ in })
                }
                
            })
        })
    }
    
    func backgroundLogin() -> Int {
        let user: CurrentUser = CurrentUser.sharedInstance
        let loginUrl = AppGroupUtilities.userDefaults()?.string(forKey: "login-url")
        if let loginUrl = loginUrl, let userauth = user.userauth, let password = user.getPassword() {
            let response = self.performLogin(loginUrl, forUser: userauth, andPassword: password, andRememberUser: user.remember, fingerprint: user.useFingerprint)
            if let response = response {
                return response.statusCode
            }
        }
        return 0
    }
    
    func performLogin(_ urlString: String, forUser username: String, andPassword password: String, andRememberUser rememberUser: Bool, fingerprint: Bool) -> HTTPURLResponse? {
        
        let defaults = AppGroupUtilities.userDefaults()!
        
        // create a plaintext string in the format username:password
        let loginString: String = "\(username):\(password)"
        
        var request = URLRequest(url: URL(string: urlString)!, cachePolicy: .reloadIgnoringCacheData, timeoutInterval: 90)
        
        let plainData = loginString.data(using: String.Encoding.utf8)
        if let encodedLoginData = plainData?.base64EncodedString() {
            let authHeader = "Basic ".appendingFormat("%@", encodedLoginData)
            request.addValue(authHeader, forHTTPHeaderField: "Authorization")
        }
        
        
        let defaultSession = URLSession(configuration: .default)
        var dataTask: URLSessionDataTask?
        let semaphore = DispatchSemaphore(value: 0)
        var responseData : Data?
        dataTask = defaultSession.dataTask(with: request as URLRequest) {
            data, response, error in
            
            DispatchQueue.main.async() {
                
                UIApplication.shared.isNetworkActivityIndicatorVisible = false
            }
            
            if let error = error {
                print(error.localizedDescription)
                responseData = nil
                
            } else if let response = response as? HTTPURLResponse {
                responseData = data
                self.httpResponse = response
            }
            semaphore.signal()
        }
        dataTask?.resume()
        let _ = semaphore.wait(timeout: .distantFuture)
        
        if let httpResponse = self.httpResponse, let data = responseData {
            let responseStatusCode = httpResponse.statusCode
            if responseStatusCode == 200 {
                let json = JSON(data: data)
                let userId = json["userId"].stringValue
                let authId = json["authId"].stringValue
                let roles = json["roles"].arrayValue.map { $0.string! }
                
                let user: CurrentUser = CurrentUser.sharedInstance
                
                user.login(auth: authId, andPassword: password, andUserid: userId, andRoles: roles, andRemember: rememberUser, fingerprint: fingerprint)
                
                var headers = [String : String]()
                for (header, value) in httpResponse.allHeaderFields {
                    headers[(header as? String)!] = value as? String
                }
                
                let cookies = HTTPCookie.cookies(withResponseHeaderFields: headers, for: httpResponse.url!)
                for cookie: HTTPCookie in cookies {
                    HTTPCookieStorage.shared.setCookie(cookie)
                }
                //save cookies
                var cookieArray: [[HTTPCookiePropertyKey : Any]] = [[HTTPCookiePropertyKey : Any]]()
                if let cookies = HTTPCookieStorage.shared.cookies {
                    for cookie in cookies {
                        var cookieProperties = [HTTPCookiePropertyKey : Any]()
                        cookieProperties[HTTPCookiePropertyKey.name] = cookie.name
                        cookieProperties[HTTPCookiePropertyKey.value] = cookie.value
                        cookieProperties[HTTPCookiePropertyKey.domain] = cookie.domain
                        cookieProperties[HTTPCookiePropertyKey.path] = cookie.path
                        cookieProperties[HTTPCookiePropertyKey.version] = cookie.version
                        if let date = cookie.expiresDate {
                            cookieProperties[HTTPCookiePropertyKey.expires] = date
                        }
                        cookieArray.append(cookieProperties)
                    }
                }
                defaults.setValue(cookieArray, forKey: "cookieArray")
                NotificationCenter.default.post(name: CurrentUser.LoginExecutorSuccessNotification, object: nil)
                // register the device if needed
                NotificationManager.registerDeviceIfNeeded()
            }
        }
        
        return self.httpResponse
    }
    
    func evaluateTouchID() -> Void {
        
        // Create the Local Authentication Context
        let touchIDContext = LAContext()
        var touchIDError : NSError?
        
        
        
        // Check if we can access local device authentication
        if touchIDContext.canEvaluatePolicy(LAPolicy.deviceOwnerAuthenticationWithBiometrics, error:&touchIDError) {
            self.useFingerprintSwitch.isEnabled = true
            allowTouchId = true
        } else {
            // Unable to access local device authentication
            switch touchIDError!.code {
            case LAError.touchIDNotEnrolled.rawValue:
                self.useFingerprintSwitch.isEnabled = false
                allowTouchId = false
                print("Touch ID is not enrolled")
            case LAError.touchIDNotAvailable.rawValue:
                self.useFingerprintSwitch.removeFromSuperview()
                self.useFingerprintSwitchLabel.removeFromSuperview()
                allowTouchId = false
                print("Touch ID not available")
            case LAError.passcodeNotSet.rawValue:
                self.useFingerprintSwitch.isEnabled = false
                allowTouchId = false
                print("Passcode has not been set")
            case LAError.touchIDLockout.rawValue:
                self.useFingerprintSwitch.removeFromSuperview()
                self.useFingerprintSwitchLabel.removeFromSuperview()
                self.useFingerprintSwitch.isOn = false
                allowTouchId = false
                print("Touch ID lockout")
            default:
                self.useFingerprintSwitch.removeFromSuperview()
                self.useFingerprintSwitchLabel.removeFromSuperview()
                allowTouchId = false
                print("Local Authentication not available")
            }
        }
        
    }
    
    func rememberUserSwitchToggled() {
        if(rememberUserSwitch.isOn) {
            self.useFingerprintSwitch.isOn = false
            self.useFingerprintSwitch.isEnabled = false
        } else {
            self.useFingerprintSwitch.isEnabled = allowTouchId
        }
    }
    
    func useFingerprintSwitchLabelToggled() {
        if(useFingerprintSwitch.isOn) {
            self.rememberUserSwitch.isOn = false
            self.rememberUserSwitch.isEnabled = false
        } else {
            self.rememberUserSwitch.isEnabled = true
        }
    }
}
